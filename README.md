# TrackFlow Platform

![Status](https://img.shields.io/badge/status-in--development-blue?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-4CAF50?style=for-the-badge)
![Kafka](https://img.shields.io/badge/Kafka-KRaft-231F20?style=for-the-badge)
![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A?style=for-the-badge)

TrackFlow is a logistics tracking platform built with Java 21, Spring Boot 3, and Apache Kafka.
It demonstrates event-driven microservices communication across three independent services.

## Services

| Service | Port | Database | Responsibility |
|---|---|---|---|
| order-service | `8081` | `orders_db` | Order lifecycle + Kafka producer |
| tracking-service | `8082` | `tracking_db` | Consumes events, stores tracking history |
| notification-service | `8083` | `notifications_db` | Consumes events, logs notifications, publishes to DLQ |

## Architecture

```
[order-service] ──── shipment-events ────► [tracking-service]
                                      └──► [notification-service] ──► shipment-events.DLQ
```

Communication is hybrid: synchronous HTTP for client-facing APIs, asynchronous Kafka for cross-service propagation.

## Prerequisites

- Java 21
- Docker + Docker Compose
- PostgreSQL running locally on `localhost:5432`
- direnv (optional, for `.envrc` support)

## Local infrastructure

PostgreSQL runs locally. Kafka runs in Docker, isolated from other local stacks.

```bash
# Start Kafka
docker compose up -d

# Stop Kafka
docker compose down
```

**Ports:**

| Service | Port | Notes |
|---|---|---|
| PostgreSQL | `5432` | Local instance |
| Kafka | `9093` | Docker — isolated from observability stack on `9092` |
| order-service | `8081` | |
| tracking-service | `8082` | |
| notification-service | `8083` | |

## Databases

Three databases on the local PostgreSQL instance:

```sql
CREATE DATABASE orders_db;
CREATE DATABASE tracking_db;
CREATE DATABASE notifications_db;
```

User: `trackflow` / `trackflow`

## Environment variables

Each service uses a `.envrc` file (see `.envrc.example` in each service directory):

```bash
export DB_USERNAME=trackflow
export DB_PASSWORD=trackflow
export DB_URL=jdbc:postgresql://localhost:5432/{service}_db
export KAFKA_BOOTSTRAP_SERVERS=localhost:9093
export SERVER_PORT=808{1,2,3}
```

## Running services

Each service runs locally via Gradle:

```bash
cd services/order-service        && ./gradlew bootRun
cd services/tracking-service     && ./gradlew bootRun
cd services/notification-service && ./gradlew bootRun
```

## Smoke test

Validates the full end-to-end flow: order creation → Kafka event → tracking + notification propagation.

```bash
./scripts/smoke.sh
```

Flow:
1. Create order in `order-service`
2. Verify event propagation to `tracking-service` and `notification-service`
3. Verify tracking history exists
4. Verify notification log exists

## API testing

Bruno collection available at `bruno/trackflow-api/`.

Open the collection in Bruno, select the `local` environment, and run requests in sequence starting from `Create Order`.

## Health checks

```bash
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8083/actuator/health
```

## Swagger UI

```
http://localhost:8081/swagger-ui/index.html
http://localhost:8082/swagger-ui/index.html
http://localhost:8083/swagger-ui/index.html
```
