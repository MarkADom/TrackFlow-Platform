package com.trackflow.notification.service;

import com.trackflow.notification.domain.NotificationLog;
import com.trackflow.notification.domain.NotificationStatus;
import com.trackflow.notification.domain.OrderStatus;
import com.trackflow.notification.dto.NotificationLogResponse;
import com.trackflow.notification.event.ShipmentEvent;
import com.trackflow.notification.exception.NotificationNotFoundException;
import com.trackflow.notification.messaging.DeadLetterPublisher;
import com.trackflow.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final DeadLetterPublisher deadLetterPublisher;

    @Value("${notification.webhook.max-retries:3}")
    private int maxRetries;

    private static final Random RANDOM = new Random();

    @Transactional
    public void processEvent(ShipmentEvent event) {
        NotificationLog notifLog = NotificationLog.builder()
                .orderId(event.getOrderId())
                .trackingCode(event.getTrackingCode())
                .status(OrderStatus.valueOf(event.getStatus().name()))
                .recipientEmail(event.getRecipientEmail())
                .notificationStatus(NotificationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        notifLog = notificationLogRepository.save(notifLog);

        boolean sent = false;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries && !sent; attempt++) {
            notifLog.setAttemptCount(attempt);
            notifLog.setLastAttemptAt(LocalDateTime.now());
            try {
                sendWebhookNotification(event);
                sent = true;
                notifLog.setNotificationStatus(NotificationStatus.SENT);
            } catch (Exception e) {
                lastException = e;
                notifLog.setNotificationStatus(NotificationStatus.FAILED);
                notifLog.setFailureReason(e.getMessage());
            }
        }

        if (!sent) {
            notifLog.setNotificationStatus(NotificationStatus.DEAD_LETTERED);
            String reason = lastException != null ? lastException.getMessage() : "Max retries exceeded";
            deadLetterPublisher.publish(event, reason);
        }

        notificationLogRepository.save(notifLog);
    }

    public void sendWebhookNotification(ShipmentEvent event) {
        if (RANDOM.nextDouble() < 0.2) {
            throw new RuntimeException("Simulated webhook failure");
        }
        log.info("NOTIFICATION SENT — Order {} is now {} → {}",
                event.getTrackingCode(), event.getStatus(), event.getRecipientEmail());
    }

    public List<NotificationLogResponse> getLogs(UUID orderId) {
        List<NotificationLog> logs = notificationLogRepository.findByOrderId(orderId);
        if (logs.isEmpty()) {
            throw new NotificationNotFoundException("No notifications found for order: " + orderId);
        }
        return logs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<NotificationLogResponse> getFailedNotifications() {
        return notificationLogRepository.findByNotificationStatus(NotificationStatus.FAILED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private NotificationLogResponse toResponse(NotificationLog n) {
        return NotificationLogResponse.builder()
                .id(n.getId())
                .orderId(n.getOrderId())
                .trackingCode(n.getTrackingCode())
                .status(n.getStatus())
                .recipientEmail(n.getRecipientEmail())
                .notificationStatus(n.getNotificationStatus())
                .attemptCount(n.getAttemptCount())
                .lastAttemptAt(n.getLastAttemptAt())
                .failureReason(n.getFailureReason())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
