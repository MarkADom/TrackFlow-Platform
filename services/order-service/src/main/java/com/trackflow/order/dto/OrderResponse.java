package com.trackflow.order.dto;

import com.trackflow.order.domain.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {

    private UUID id;
    private String trackingCode;
    private String origin;
    private String destination;
    private String recipientName;
    private String recipientEmail;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
