package com.trackflow.notification.messaging;

import com.trackflow.notification.domain.OrderStatus;
import com.trackflow.notification.event.ShipmentEvent;
import com.trackflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ShipmentEventConsumer shipmentEventConsumer;

    @Test
    void should_delegateToNotificationService_when_validEventReceived() {
        // Arrange
        ShipmentEvent event = buildEvent();

        // Act
        shipmentEventConsumer.consume(event);

        // Assert
        verify(notificationService, times(1)).processEvent(event);
    }

    @Test
    void should_passEventUnmodified_when_delegatingToService() {
        // Arrange
        ShipmentEvent event = buildEvent();
        ArgumentCaptor<ShipmentEvent> captor = ArgumentCaptor.forClass(ShipmentEvent.class);

        // Act
        shipmentEventConsumer.consume(event);

        // Assert
        verify(notificationService).processEvent(captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }

    @Test
    void should_callProcessEventOnce_when_singleEventConsumed() {
        // Arrange
        ShipmentEvent event = buildEvent();

        // Act
        shipmentEventConsumer.consume(event);

        // Assert
        verify(notificationService, times(1)).processEvent(any());
        verifyNoMoreInteractions(notificationService);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private ShipmentEvent buildEvent() {
        return ShipmentEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .trackingCode("TF-CONS01")
                .status(OrderStatus.IN_TRANSIT)
                .origin("Gdansk")
                .destination("Wroclaw")
                .recipientEmail("consumer@example.com")
                .notes("Event consumer test")
                .occurredAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }
}
