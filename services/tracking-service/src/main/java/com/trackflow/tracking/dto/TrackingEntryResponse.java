package com.trackflow.tracking.dto;

import com.trackflow.tracking.domain.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TrackingEntryResponse {

    private UUID id;
    private UUID orderId;
    private String trackingCode;
    private OrderStatus status;
    private String origin;
    private String destination;
    private String recipientEmail;
    private String notes;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
}
