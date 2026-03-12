# Architecture

[← README](../README.md)

## System Overview

TrackFlow is a logistics tracking platform that manages shipment orders from creation through delivery. It demonstrates event-driven communication across three independent microservices: a client-facing order API, a tracking history service, and a notification service.

## Service Responsibilities

| Service | Responsibility |
|---|---|
| **order-service** | Accepts order creation and status update requests via REST. Owns the order lifecycle and publishes a `ShipmentEvent` to Kafka on every status change. |
| **tracking-service** | Consumes `ShipmentEvent` from Kafka and persists a timestamped tracking record for each status transition. Exposes a REST API for querying tracking history by tracking code. |
| **notification-service** | Consumes `ShipmentEvent` from Kafka and dispatches webhook notifications. Retries on failure; dead-letters events that exhaust retries by publishing to `shipment-events.DLQ`. |

## Communication Style

- **Synchronous HTTP** — client-facing REST APIs on each service (ports 8081, 8082, 8083).
- **Asynchronous Kafka** — cross-service event propagation; order-service produces, tracking-service and notification-service consume independently.

## Event Flow

```
                        ┌─────────────────────┐
                        │    order-service     │
                        │       :8081          │
                        └─────────┬───────────┘
                                  │ publishes ShipmentEvent
                                  ▼
                        ┌─────────────────────┐
                        │   shipment-events    │
                        │  (3 partitions)      │
                        └──────┬──────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
  ┌─────────────────────┐       ┌──────────────────────────┐
  │  tracking-service   │       │  notification-service    │
  │       :8082         │       │         :8083            │
  │  tracking_db        │       │   notifications_db       │
  └─────────────────────┘       └──────────┬───────────────┘
                                           │ on exhausted retries
                                           ▼
                                ┌──────────────────────┐
                                │  shipment-events.DLQ │
                                └──────────────────────┘
```

## Data Ownership

Each service owns an isolated PostgreSQL database. No service queries another service's database directly.

| Service | Database |
|---|---|
| order-service | `orders_db` |
| tracking-service | `tracking_db` |
| notification-service | `notifications_db` |

## Why Kafka

- **Multiple independent consumers** — tracking-service and notification-service consume the same events without coupling to each other or to order-service.
- **Decoupled failure domains** — a notification-service outage does not affect order creation or tracking record persistence.
- **Guaranteed delivery** — events are retained in the topic; consumers can lag and catch up without data loss.
- **Dead-letter support** — `shipment-events.DLQ` captures events that exhaust notification retries, enabling future reprocessing without blocking the main topic.

## Observability

Each service exposes metrics, logs, and traces integrated with the local Grafana LGTM stack.

| Concern | Tool | Details |
|---|---|---|
| Metrics | Prometheus + Micrometer | `/actuator/prometheus` on each service; scraped via file-based service discovery |
| Logs | Loki + loki4j | `loki-logback-appender:1.5.2`; configured in `logback-spring.xml` with labels `app`, `host`, `level` |
| Traces | Tempo + OpenTelemetry | OTLP HTTP exporter to `localhost:4318`; sampling probability 1.0 |
| Dashboards | Grafana | `http://localhost:3000`; dashboard source at `docs/grafana/trackflow-dashboard.json` |

The observability stack (Prometheus, Loki, Tempo, Grafana) runs separately from the TrackFlow services. It is not part of TrackFlow's `docker-compose.yml` — see the README for setup instructions.

### Grafana dashboard

The pre-built dashboard at `docs/grafana/trackflow-dashboard.json` includes five panels:

| Panel | Type |
|---|---|
| Total Orders Created | Stat (Prometheus) |
| Failed Notifications | Stat (Prometheus) |
| HTTP Request Rate | Time series (Prometheus) |
| JVM Heap Memory | Time series (Prometheus) |
| Service Logs | Logs (Loki) |
