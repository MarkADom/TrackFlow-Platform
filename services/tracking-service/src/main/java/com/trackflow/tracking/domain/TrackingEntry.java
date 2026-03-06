package com.trackflow.tracking.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tracking_entries",
        indexes = @Index(name = "idx_tracking_order_id", columnList = "order_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String origin;

    private String destination;

    private String recipientEmail;

    private String notes;

    private LocalDateTime occurredAt;

    private LocalDateTime receivedAt;
}
