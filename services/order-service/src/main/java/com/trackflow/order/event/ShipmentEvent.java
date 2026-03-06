package com.trackflow.order.event;

import com.trackflow.order.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentEvent {

    private UUID eventId;
    private UUID orderId;
    private String trackingCode;
    private OrderStatus status;
    private String origin;
    private String destination;
    private String recipientEmail;
    private String notes;
    private LocalDateTime occurredAt;
}
