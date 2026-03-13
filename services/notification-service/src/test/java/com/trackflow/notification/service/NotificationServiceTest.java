package com.trackflow.notification.service;

import com.trackflow.notification.domain.NotificationLog;
import com.trackflow.notification.domain.NotificationStatus;
import com.trackflow.notification.domain.OrderStatus;
import com.trackflow.notification.dto.NotificationLogResponse;
import com.trackflow.notification.event.ShipmentEvent;
import com.trackflow.notification.exception.NotificationNotFoundException;
import com.trackflow.notification.messaging.DeadLetterPublisher;
import com.trackflow.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private DeadLetterPublisher deadLetterPublisher;

    // Created manually so we can spy on sendWebhookNotification
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = spy(new NotificationService(notificationLogRepository, deadLetterPublisher));
        ReflectionTestUtils.setField(notificationService, "maxRetries", 3);
        // lenient: this stub is only relevant for processEvent tests, not for getLogs/getFailedNotifications
        lenient().when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ─── getLogs ──────────────────────────────────────────────────────────

    @Test
    void should_returnLogs_when_logsExistForOrderId() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        NotificationLog log = buildLog(orderId, NotificationStatus.SENT);
        when(notificationLogRepository.findByOrderId(orderId)).thenReturn(List.of(log));

        // Act
        List<NotificationLogResponse> result = notificationService.getLogs(orderId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo(orderId);
    }

    @Test
    void should_throwNotificationNotFoundException_when_noLogsFoundForOrderId() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(notificationLogRepository.findByOrderId(orderId)).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThatThrownBy(() -> notificationService.getLogs(orderId))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void should_mapAllFields_when_convertingLogToResponse() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        NotificationLog log = buildLog(orderId, NotificationStatus.SENT);
        when(notificationLogRepository.findByOrderId(orderId)).thenReturn(List.of(log));

        // Act
        NotificationLogResponse response = notificationService.getLogs(orderId).get(0);

        // Assert
        assertThat(response.getTrackingCode()).isEqualTo(log.getTrackingCode());
        assertThat(response.getNotificationStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.getRecipientEmail()).isEqualTo(log.getRecipientEmail());
        assertThat(response.getAttemptCount()).isEqualTo(log.getAttemptCount());
    }

    // ─── getFailedNotifications ───────────────────────────────────────────

    @Test
    void should_returnFailedLogs_when_failedNotificationsExist() {
        // Arrange
        NotificationLog log = buildLog(UUID.randomUUID(), NotificationStatus.FAILED);
        when(notificationLogRepository.findByNotificationStatus(NotificationStatus.FAILED))
                .thenReturn(List.of(log));

        // Act
        List<NotificationLogResponse> result = notificationService.getFailedNotifications();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNotificationStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void should_returnEmptyList_when_noFailedNotificationsExist() {
        // Arrange
        when(notificationLogRepository.findByNotificationStatus(NotificationStatus.FAILED))
                .thenReturn(Collections.emptyList());

        // Act
        List<NotificationLogResponse> result = notificationService.getFailedNotifications();

        // Assert
        assertThat(result).isEmpty();
    }

    // ─── processEvent — initial PENDING save ─────────────────────────────

    @Test
    void should_savePendingLog_when_processingEvent() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doNothing().when(notificationService).sendWebhookNotification(any());

        // Capture notification status at the moment each save() is invoked
        List<NotificationStatus> statusesAtSaveTime = new ArrayList<>();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            statusesAtSaveTime.add(log.getNotificationStatus());
            return log;
        });

        // Act
        notificationService.processEvent(event);

        // Assert — first save must capture PENDING
        assertThat(statusesAtSaveTime).isNotEmpty();
        assertThat(statusesAtSaveTime.get(0)).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void should_setOrderIdFromEvent_when_creatingNotificationLog() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doNothing().when(notificationService).sendWebhookNotification(any());
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        // Act
        notificationService.processEvent(event);

        // Assert
        verify(notificationLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(event.getOrderId());
    }

    // ─── processEvent — success path ──────────────────────────────────────

    @Test
    void should_setStatusToSent_when_webhookSucceedsOnFirstAttempt() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doNothing().when(notificationService).sendWebhookNotification(any());

        List<NotificationStatus> statusesAtSaveTime = new ArrayList<>();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            statusesAtSaveTime.add(log.getNotificationStatus());
            return log;
        });

        // Act
        notificationService.processEvent(event);

        // Assert — last save must persist SENT
        assertThat(statusesAtSaveTime.get(statusesAtSaveTime.size() - 1))
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void should_setAttemptCountToOne_when_webhookSucceedsOnFirstAttempt() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doNothing().when(notificationService).sendWebhookNotification(any());
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        // Act
        notificationService.processEvent(event);

        // Assert
        verify(notificationLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void should_notPublishToDlq_when_webhookEventuallySucceeds() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doNothing().when(notificationService).sendWebhookNotification(any());

        // Act
        notificationService.processEvent(event);

        // Assert
        verify(deadLetterPublisher, never()).publish(any(), any());
    }

    // ─── processEvent — retry then success ────────────────────────────────

    @Test
    void should_retryAndSetSentStatus_when_webhookFailsOnceThenSucceeds() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doThrow(new RuntimeException("transient failure"))
                .doNothing()
                .when(notificationService).sendWebhookNotification(any());

        List<NotificationStatus> statusesAtSaveTime = new ArrayList<>();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            statusesAtSaveTime.add(log.getNotificationStatus());
            return log;
        });

        // Act
        notificationService.processEvent(event);

        // Assert
        verify(notificationService, times(2)).sendWebhookNotification(event);
        assertThat(statusesAtSaveTime.get(statusesAtSaveTime.size() - 1))
                .isEqualTo(NotificationStatus.SENT);
    }

    // ─── processEvent — all retries exhausted ─────────────────────────────

    @Test
    void should_setStatusToDeadLettered_when_allRetriesExhausted() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doThrow(new RuntimeException("persistent failure"))
                .when(notificationService).sendWebhookNotification(any());

        List<NotificationStatus> statusesAtSaveTime = new ArrayList<>();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            statusesAtSaveTime.add(log.getNotificationStatus());
            return log;
        });

        // Act
        notificationService.processEvent(event);

        // Assert
        assertThat(statusesAtSaveTime.get(statusesAtSaveTime.size() - 1))
                .isEqualTo(NotificationStatus.DEAD_LETTERED);
    }

    @Test
    void should_publishToDlq_when_allRetriesExhausted() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doThrow(new RuntimeException("persistent failure"))
                .when(notificationService).sendWebhookNotification(any());

        // Act
        notificationService.processEvent(event);

        // Assert
        verify(deadLetterPublisher, times(1)).publish(eq(event), anyString());
    }

    @Test
    void should_callSendExactlyMaxRetries_when_allAttemptsFailBeforeDlq() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doThrow(new RuntimeException("fail")).when(notificationService).sendWebhookNotification(any());

        // Act
        notificationService.processEvent(event);

        // Assert
        verify(notificationService, times(3)).sendWebhookNotification(event);
    }

    @Test
    void should_setFailureReason_when_webhookThrowsException() {
        // Arrange
        ShipmentEvent event = buildEvent();
        doThrow(new RuntimeException("webhook timeout"))
                .when(notificationService).sendWebhookNotification(any());

        List<String> capturedReasons = new ArrayList<>();
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            if (log.getFailureReason() != null) capturedReasons.add(log.getFailureReason());
            return log;
        });

        // Act
        notificationService.processEvent(event);

        // Assert
        assertThat(capturedReasons).isNotEmpty();
        assertThat(capturedReasons.get(0)).isEqualTo("webhook timeout");
    }

    // ─── sendWebhookNotification — both branches ──────────────────────────

    @Test
    void should_eventuallySucceedWithoutException_when_sendWebhookCalledRepeatedly() {
        // Arrange — 80% success rate; 50 calls covers both branches with near-certainty
        ShipmentEvent event = buildEvent();
        boolean successObserved = false;

        // Act
        for (int i = 0; i < 50 && !successObserved; i++) {
            try {
                NotificationService real = new NotificationService(notificationLogRepository, deadLetterPublisher);
                real.sendWebhookNotification(event);
                successObserved = true;
            } catch (RuntimeException ignored) {
            }
        }

        // Assert
        assertThat(successObserved).isTrue();
    }

    @Test
    void should_throwRuntimeExceptionWithCorrectMessage_when_randomFailureBranchTriggered() {
        // Arrange — 20% failure rate; 50 calls covers the failure branch with near-certainty
        ShipmentEvent event = buildEvent();
        RuntimeException captured = null;

        // Act
        for (int i = 0; i < 50 && captured == null; i++) {
            try {
                NotificationService real = new NotificationService(notificationLogRepository, deadLetterPublisher);
                real.sendWebhookNotification(event);
            } catch (RuntimeException e) {
                captured = e;
            }
        }

        // Assert
        assertThat(captured).isNotNull();
        assertThat(captured.getMessage()).isEqualTo("Simulated webhook failure");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private NotificationLog buildLog(UUID orderId, NotificationStatus status) {
        return NotificationLog.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingCode("TF-TEST01")
                .status(OrderStatus.IN_TRANSIT)
                .recipientEmail("user@example.com")
                .notificationStatus(status)
                .attemptCount(1)
                .lastAttemptAt(LocalDateTime.now().minusMinutes(5))
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    private ShipmentEvent buildEvent() {
        return ShipmentEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .trackingCode("TF-EVT001")
                .status(OrderStatus.PICKED_UP)
                .origin("Warsaw")
                .destination("Krakow")
                .recipientEmail("recipient@example.com")
                .notes("In transit")
                .occurredAt(LocalDateTime.now().minusMinutes(15))
                .build();
    }
}
