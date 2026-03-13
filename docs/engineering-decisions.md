# Engineering Decisions

[← README](../README.md)

Notable design decisions, the options considered, and the rationale behind each.

---

## 1. Kafka Type Headers Disabled

**Decision:** Producers set `ADD_TYPE_INFO_HEADERS=false`; consumers set `USE_TYPE_INFO_HEADERS=false` and configure `VALUE_DEFAULT_TYPE` to their local event class.

**Problem:** Spring Kafka's `JsonDeserializer` uses the `__TypeId__` header to resolve the target class. When the header contains the producer's fully-qualified class name (e.g., `com.trackflow.order.event.ShipmentEvent`), the consumer — which has no dependency on the producer's package — throws a `ClassNotFoundException`.

**Options considered:**
- Share a common `events` library across services — adds inter-service build coupling.
- Trusted packages allowlist — still relies on matching class names across independent packages.
- Disable type headers, set `VALUE_DEFAULT_TYPE` per consumer — each service deserializes into its own local class; no shared dependency required.

**Rationale:** Disabling type headers keeps services fully independent. The event schema is the contract; class identity is an internal detail.

---

## 2. PostgreSQL Local, Kafka in Docker

**Decision:** PostgreSQL runs on the host machine (`localhost:5432`). Kafka runs in Docker on port `9093`.

**Rationale:** PostgreSQL was already running locally with existing databases. Running it in Docker would add unnecessary volume management and container lifecycle overhead during development. Kafka is isolated in Docker because it has no local instance; port `9093` avoids conflict with an observability stack already occupying `9092`.

---

## 3. Forward-Only Status Transitions via `ordinal()`

**Decision:** Status transition validation compares `OrderStatus` ordinal values. A new status is valid only if its ordinal is greater than the current one, except `FAILED` which is reachable from any state.

**Options considered:**
- Explicit transition map (e.g., `Map<OrderStatus, Set<OrderStatus>>`) — more flexible, supports branching.
- Ordinal comparison — simpler, self-documenting for a strictly linear lifecycle.

**Rationale:** The shipment lifecycle is linear and unlikely to branch in this domain. Ordinal comparison is concise and makes the ordering explicit in the enum declaration itself. The trade-off is that the approach does not extend to non-linear workflows without a redesign.

---

## 4. `receivedAt` Set by Consumer, Not from Event Payload

**Decision:** `tracking-service` sets `receivedAt` to `LocalDateTime.now()` when it consumes an event, rather than copying a timestamp from the event payload.

**Rationale:** Using the consumer's local clock for the consumer's own timestamp avoids clock skew between services. `occurredAt` (set by order-service) records when the status change happened; `receivedAt` records when tracking-service processed it. These are different facts and should have independent sources.

---

## 5. 20% Simulated Failure Rate in Notification Service

**Decision:** `NotificationService.sendWebhookNotification()` simulates a 20% random failure rate before retrying up to `notification.webhook.max-retries` times (default: 3). Events that exhaust retries are marked `DEAD_LETTERED` and published to `shipment-events.DLQ`.

**Rationale:** The platform has no real external webhook endpoint. The simulated failure rate exercises the retry and dead-letter path in a self-contained way, demonstrating that failure handling works end-to-end without an external dependency.

---

## 6. Spring Boot Services Run Locally, Not in Docker

**Decision:** The three Spring Boot services run on the host via `./gradlew bootRun`. Only Kafka runs in Docker.

**Rationale:** Running all services in Docker during active development adds friction: image rebuilds on every code change, port-mapping complexity, and harder debugger attachment. Docker is reserved for infrastructure (Kafka) whose configuration is stable. Services are containerizable but not containerized by default.

---

## 7. Observability Stack Is External to TrackFlow

**Decision:** Prometheus, Loki, Tempo, and Grafana run from a separate Compose project at `/mnt/ai_core/infra/observability/` and are not included in TrackFlow's `docker-compose.yml`.

**Rationale:** The observability stack is shared across multiple local projects. Bundling it inside TrackFlow would cause port conflicts and duplicate resource usage whenever any other project needed the same tools. TrackFlow services treat it as an external dependency and connect to its well-known ports (`9090`, `3100`, `4318`, `3000`).

---

## 8. OTLP HTTP Exporter (port 4318) Instead of gRPC (port 4317)

**Decision:** All three services export traces via OTLP over HTTP to `localhost:4318`, using `opentelemetry-spring-boot-starter:2.10.0` and `micrometer-tracing-bridge-otel`.

**Problem:** The local Tempo instance is configured to receive OTLP over HTTP. The default `otel.exporter.otlp.endpoint` port (`4317`) targets the gRPC receiver; pointing at it with the HTTP exporter causes a silent connection failure with no traces appearing in Tempo.

**Rationale:** Using the HTTP endpoint (`4318`) matches the Tempo configuration without changing the observability stack. Sampling probability is set to `1.0` so all traces are captured during development.

---

## 9. Explicit Batch Settings on the Loki Appender

**Decision:** `logback-spring.xml` on each service explicitly sets `batchMaxItems=100` and `batchTimeoutMs=5000` on the loki4j appender rather than relying on defaults.

**Problem:** On low-traffic services the appender's default batch never filled, and the timeout was long enough that log lines were delayed or dropped entirely during short-lived test runs.

**Rationale:** Explicit settings make flush behaviour predictable regardless of traffic volume. The chosen values (`100` items, `5 s`) are conservative enough to avoid back-pressure while ensuring logs arrive in Loki within a few seconds of being emitted.


## Future Considerations

Potential future changes include:

- introducing schema versioning for Kafka events
- containerizing services for production deployment
- externalizing notification providers
