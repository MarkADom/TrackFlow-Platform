package com.trackflow.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_logs",
        indexes = @Index(name = "idx_notif_order_id", columnList = "order_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    private NotificationStatus notificationStatus;

    @Builder.Default
    private int attemptCount = 0;

    private LocalDateTime lastAttemptAt;

    private String failureReason;

    private LocalDateTime createdAt;
}
