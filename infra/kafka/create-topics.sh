#!/usr/bin/env bash
# ============================================================
# MedCore — creacion de los 9 topicos Kafka.
# Se ejecuta desde el host, con el contenedor de Kafka levantado:
#   bash infra/kafka/create-topics.sh
# Es idempotente: --if-not-exists no falla si el topico ya existe.
# ============================================================
set -euo pipefail

# Git Bash en Windows traduce /opt/... a C:/Program Files/Git/opt/...
# dentro de docker exec. Esto lo desactiva.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

CONTAINER="${KAFKA_CONTAINER:-medcore-kafka}"
BOOTSTRAP="${KAFKA_INTERNAL_BOOTSTRAP:-kafka:9092}"
PARTITIONS="${KAFKA_PARTITIONS:-3}"
REPLICATION=1   # un solo broker en desarrollo

TOPICS=(
  patient.created
  appointment.created
  appointment.cancelled
  lab-order.created
  sample.registered
  result.registered
  result.critical
  stock.low
  stock.expired
  diagnosis.registered
)

echo "Esperando a que Kafka responda en ${BOOTSTRAP}..."
for i in $(seq 1 30); do
  if docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
       --bootstrap-server "$BOOTSTRAP" --list >/dev/null 2>&1; then
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "Kafka no respondio tras 30 intentos. Revisa: docker logs ${CONTAINER}" >&2
    exit 1
  fi
  sleep 2
done

for topic in "${TOPICS[@]}"; do
  docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$PARTITIONS" \
    --replication-factor "$REPLICATION"
  echo "  topico listo: ${topic}"
done

echo
echo "Topicos existentes:"
docker exec "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" --list
