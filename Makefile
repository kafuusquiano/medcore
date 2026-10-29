# ============================================================
# MedCore — atajos de desarrollo.
# En Windows se ejecutan desde Git Bash.
# Si no tienes make, cada receta muestra el comando equivalente
# con "make help".
# ============================================================

SHELL := /bin/bash
COMPOSE := docker compose
COMPOSE_INFRA := docker compose -f docker-compose.infra.yml
MVN := ./mvnw
BACKEND := medcore-backend

.DEFAULT_GOAL := help
.PHONY: help up down restart logs ps infra infra-down topics build test clean \
        health eureka reset tunnel

help:  ## Muestra esta ayuda
	@echo "MedCore — comandos disponibles:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

# ---------- Stack completo ----------

up:  ## Levanta todo el sistema (construye imagenes si hace falta)
	$(COMPOSE) up -d --build
	@echo "Esperando a que el gateway quede sano..."
	@until curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do sleep 3; done
	@echo "Listo. Gateway en http://localhost:8080"

down:  ## Detiene el sistema y elimina los contenedores
	$(COMPOSE) down

restart: down up  ## Reinicia el sistema completo

logs:  ## Sigue los logs de todos los servicios
	$(COMPOSE) logs -f --tail=100

ps:  ## Estado de los contenedores
	$(COMPOSE) ps

# ---------- Solo infraestructura ----------

infra:  ## Levanta solo Postgres, MongoDB, Redis y Kafka
	$(COMPOSE_INFRA) up -d
	@$(MAKE) --no-print-directory topics

infra-down:  ## Detiene la infraestructura
	$(COMPOSE_INFRA) down

topics:  ## Crea los 9 topicos Kafka (idempotente)
	bash infra/kafka/create-topics.sh

# ---------- Backend ----------

build:  ## Compila el backend sin ejecutar tests
	cd $(BACKEND) && mvn -B package -DskipTests

test:  ## Ejecuta los tests del backend
	cd $(BACKEND) && mvn -B verify

clean:  ## Limpia los artefactos de compilacion
	cd $(BACKEND) && mvn -B clean

# ---------- Verificacion ----------

health:  ## Consulta la salud de los servicios de infraestructura
	@for s in eureka:8761 config:8888 gateway:8080; do \
	  nombre=$${s%%:*}; puerto=$${s##*:}; \
	  estado=$$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$$puerto/actuator/health 2>/dev/null); \
	  printf '  %-10s %s\n' "$$nombre" "$$estado"; \
	done

eureka:  ## Lista los servicios registrados en Eureka
	@curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps \
	  | python -c "import sys,json; a=json.load(sys.stdin)['applications'].get('application',[]); print('\n'.join('  '+x['name'].lower()+'  '+x['instance'][0]['status'] if isinstance(x['instance'],list) else '  '+x['name'].lower()+'  '+x['instance']['status'] for x in a)) if a else print('  (ninguno registrado)')"

# ---------- Despliegue ----------

tunnel:  ## Abre el tunel ngrok hacia el gateway
	@test -n "$(NGROK_DOMAIN)" || { echo "Define NGROK_DOMAIN con tu dominio estatico reservado"; exit 1; }
	ngrok http --url=$(NGROK_DOMAIN) 8080

# ---------- Peligroso ----------

reset:  ## BORRA los volumenes de datos y vuelve a levantar desde cero
	@echo "Esto elimina TODOS los datos de Postgres, MongoDB y Redis."
	@read -p "Escribe 'si' para continuar: " ok; [ "$$ok" = "si" ] || exit 1
	$(COMPOSE) down -v
	$(COMPOSE_INFRA) down -v
	@echo "Volumenes eliminados. Usa 'make up' o 'make infra' para empezar de nuevo."
