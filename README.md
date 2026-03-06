# TrackFlow Platform (Local Run)

This repository now supports deterministic local startup of the full TrackFlow microservices platform:

- `order-service`
- `tracking-service`
- `notification-service`
- PostgreSQL
- Kafka

## Why this local setup

For simplicity and repeatability, local startup is containerized with one root `docker-compose.yml`.

- **Single PostgreSQL instance with per-service databases** (`orders_db`, `tracking_db`, `notifications_db`):
    - Lower local resource usage than 3 Postgres containers.
    - Keeps clean service-level data separation.
    - Keeps operations simple for this sprint.
- **Single Kafka broker (KRaft mode)**:
    - Sufficient for local development and event flow testing.

## Prerequisites

- Docker + Docker Compose
- At least 6 GB RAM available for Docker

> No local Java/Gradle install is required because services run in containers with Gradle JDK 21 images.

## Ports

### Platform infrastructure

- PostgreSQL: `5432`
- Kafka broker: `9092`

### Services

- order-service: `8081`
- tracking-service: `8082`
- notification-service: `8083`

## Environment variables used by services

Configured in `docker-compose.yml`:

- `SERVER_PORT`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`

Default values are wired to local Docker service names:

- Postgres host: `postgres`
- Kafka host: `kafka`

## Startup

```bash
./scripts/up.sh
```

What it does:
1. `docker compose up -d`
2. waits for actuator health endpoints of all 3 services

## Smoke test (end-to-end)

```bash
./scripts/smoke.sh
```

Smoke flow:
1. Create order in `order-service`
2. Verify event propagation to:
    - `tracking-service`
    - `notification-service`
3. Verify tracking history exists
4. Verify notification log exists

## Shutdown

```bash
./scripts/down.sh
```

This removes containers, networks, and volumes created by Compose.

## Useful commands

Tail logs:

```bash
docker compose logs -f order-service tracking-service notification-service kafka postgres
```

Check health quickly:

```bash
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8083/actuator/health
```
