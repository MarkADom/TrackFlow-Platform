#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

wait_for_http_health() {
  local url="$1"
  local name="$2"
  local retries="${3:-120}"

  echo "Waiting for ${name} at ${url} ..."
  for ((i=1; i<=retries; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "${name} is healthy"
      return 0
    fi
    sleep 2
  done

  echo "ERROR: ${name} did not become healthy in time"
  return 1
}

wait_for_container_health() {
  local service="$1"
  local retries="${2:-90}"

  echo "Waiting for ${service} container health ..."
  for ((i=1; i<=retries; i++)); do
    local status
    status="$(docker compose ps --format json "$service" | python3 -c 'import json,sys; data=json.load(sys.stdin); print((data[0].get("Health") or data[0].get("State") or "").lower())' 2>/dev/null || true)"

    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      echo "${service} container status: ${status}"
      return 0
    fi
    sleep 2
  done

  echo "ERROR: ${service} container did not become healthy in time"
  docker compose logs --tail=100 "$service" || true
  return 1
}

ensure_database_simple() {
  local db_name="$1"
  if ! docker compose exec -T postgres psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${db_name}'" | grep -q 1; then
    echo "Creating missing database: ${db_name}"
    docker compose exec -T postgres createdb -U postgres "$db_name"
  fi
}

echo "Starting TrackFlow infrastructure (PostgreSQL + Kafka)..."
docker compose up -d postgres kafka

wait_for_container_health "postgres"
wait_for_container_health "kafka"

echo "Ensuring per-service databases exist..."
ensure_database_simple "orders_db"
ensure_database_simple "tracking_db"
ensure_database_simple "notifications_db"

echo "Starting TrackFlow services..."
docker compose up -d order-service tracking-service notification-service

wait_for_http_health "http://localhost:8081/actuator/health" "order-service"
wait_for_http_health "http://localhost:8082/actuator/health" "tracking-service"
wait_for_http_health "http://localhost:8083/actuator/health" "notification-service"

echo "All services are up."
