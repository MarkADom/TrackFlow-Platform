package com.trackflow.notification.dto;

import com.trackflow.notification.domain.NotificationStatus;
import com.trackflow.notification.domain.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationLogResponse {

    private UUID id;
    private UUID orderId;
    private String trackingCode;
    private OrderStatus status;
    private String recipientEmail;
    private NotificationStatus notificationStatus;
    private int attemptCount;
    private LocalDateTime lastAttemptAt;
    private String failureReason;
    private LocalDateTime createdAt;
}
