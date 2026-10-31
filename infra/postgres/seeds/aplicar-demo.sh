#!/usr/bin/env bash
#
# Aplica los datos de demostracion a las bases del stack local.
#
# Hace falta el stack levantado, porque el seed se aplica DESPUES de que
# Flyway haya creado las tablas: los usuarios de demostracion referencian la
# tabla de roles, que la crea la migracion V1 de auth-service.
#
#   bash infra/postgres/seeds/aplicar-demo.sh
#
# Es idempotente: se puede ejecutar varias veces sin duplicar nada.
#
# ADVERTENCIA: las contrasenas de los usuarios de demostracion son publicas y
# estan en demo_seed.sql. Esto es solo para desarrollo en local.

set -euo pipefail

CONTENEDOR="${POSTGRES_CONTAINER:-medcore-postgres}"
USUARIO="${POSTGRES_USER:-medcore}"
SEED="$(dirname "$0")/demo_seed.sql"

# Git Bash en Windows traduce las rutas que parecen de Unix al pasarlas a
# docker exec, y convierte /dev/stdin en una ruta de Windows que el contenedor
# no entiende.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

if [ ! -f "$SEED" ]; then
    echo "No encuentro $SEED" >&2
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR"; then
    echo "El contenedor $CONTENEDOR no esta en marcha." >&2
    echo "Levanta el stack primero: docker compose up -d" >&2
    exit 1
fi

# Las migraciones tienen que haber corrido ya: el seed referencia la tabla de
# roles, que crea Flyway.
echo "Comprobando que las migraciones ya corrieron..."
if ! docker exec "$CONTENEDOR" psql -U "$USUARIO" -d auth_db -tAc \
        "SELECT 1 FROM information_schema.tables WHERE table_name = 'roles'" \
        2>/dev/null | grep -q 1; then
    echo "La tabla roles de auth_db no existe todavia." >&2
    echo "Espera a que auth-service arranque y vuelve a intentarlo." >&2
    exit 1
fi

echo "Aplicando los datos de demostracion..."
docker exec -i "$CONTENEDOR" psql -U "$USUARIO" -d postgres \
    -v ON_ERROR_STOP=1 < "$SEED"

# Los horarios no se pueden sembrar con un INSERT: crear uno genera a la vez
# los huecos de las proximas semanas, y sin huecos no se puede reservar
# ninguna cita. Se crean por la API, que es la unica forma de que esa
# generacion ocurra.
RAIZ="$(cd "$(dirname "$0")/../../.." && pwd)"
PREPARAR="$RAIZ/scripts/preparar_demo.py"

if [ ! -f "$PREPARAR" ]; then
    echo "" >&2
    echo "No encuentro $PREPARAR; los horarios no se han creado." >&2
    echo "Sin horarios no hay citas que reservar." >&2
    exit 1
fi

CLAVE_ADMIN="$(grep -E '^ADMIN_INITIAL_PASSWORD=' "$RAIZ/.env" 2>/dev/null | cut -d= -f2-)"

if [ -z "$CLAVE_ADMIN" ]; then
    echo "" >&2
    echo "ADMIN_INITIAL_PASSWORD no esta en el .env." >&2
    echo "Los horarios se crean por la API y hace falta entrar." >&2
    exit 1
fi

echo ""
echo "Creando los horarios medicos por la API..."

# En Git Bash la ruta es de estilo Unix (/c/Users/...) y el python.exe de
# Windows no la entiende. cygpath la traduce; fuera de Git Bash no existe y la
# ruta ya vale tal cual.
if command -v cygpath > /dev/null 2>&1; then
    PREPARAR="$(cygpath -w "$PREPARAR")"
fi

python "$PREPARAR" "${ADMIN_DNI:-00000001}" "$CLAVE_ADMIN"
