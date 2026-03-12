# Event Contract

[← README](../README.md)

## ShipmentEvent

The canonical event published by **order-service** and consumed by **tracking-service** and **notification-service**.

### Schema

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | Unique identifier for this event instance |
| `orderId` | UUID | Identifier of the originating order |
| `trackingCode` | String | Format: `TF-XXXXXX` (TF- prefix + 6 random uppercase alphanumeric chars) |
| `status` | OrderStatus | See enum values below |
| `origin` | String | Shipment origin location |
| `destination` | String | Shipment destination location |
| `recipientEmail` | String | Email address for notifications |
| `notes` | String | Optional; nullable |
| `occurredAt` | LocalDateTime | Timestamp of the status change, set by order-service |

### OrderStatus Enum

```
CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
                                                    ↘
                                              FAILED (reachable from any state)
```

Transitions are forward-only, enforced by ordinal comparison. `FAILED` is the only non-linear state.

## Topics

| Topic | Partitions | Replication Factor | Producer | Consumers |
|---|---|---|---|---|
| `shipment-events` | 3 | 1 | order-service | tracking-service, notification-service |
| `shipment-events.DLQ` | 1 | 1 | notification-service | — |

## Consumer Groups

| Group ID | Service |
|---|---|
| `tracking-service-group` | tracking-service |
| `notification-service-group` | notification-service |

## Serialization

Events are serialized as JSON. Kafka type headers are disabled on both sides:

- **Producers** — `ADD_TYPE_INFO_HEADERS=false`
- **Consumers** — `USE_TYPE_INFO_HEADERS=false` + `VALUE_DEFAULT_TYPE` set to the local event class

This prevents cross-service `ClassNotFoundException` when the deserializer attempts to resolve the producer's fully-qualified class name. See [engineering-decisions.md](engineering-decisions.md) for rationale.
