package com.trackflow.tracking.messaging;

import com.trackflow.tracking.domain.OrderStatus;
import com.trackflow.tracking.event.ShipmentEvent;
import com.trackflow.tracking.service.TrackingService;
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
    private TrackingService trackingService;

    @InjectMocks
    private ShipmentEventConsumer shipmentEventConsumer;

    @Test
    void should_delegateToTrackingService_when_validEventReceived() {
        // Arrange
        ShipmentEvent event = buildEvent(OrderStatus.PICKED_UP);

        // Act
        shipmentEventConsumer.consume(event);

        // Assert
        verify(trackingService, times(1)).recordEvent(event);
    }

    @Test
    void should_passEventUnmodified_when_delegatingToService() {
        // Arrange
        ShipmentEvent event = buildEvent(OrderStatus.IN_TRANSIT);
        ArgumentCaptor<ShipmentEvent> captor = ArgumentCaptor.forClass(ShipmentEvent.class);

        // Act
        shipmentEventConsumer.consume(event);

        // Assert
        verify(trackingService).recordEvent(captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }

    @Test
    void should_notSetReceivedAt_when_consumerProcessesEvent() {
        // Arrange — receivedAt is NOT a field on ShipmentEvent; only TrackingEntry has it.
        // The consumer must not enrich the event; that responsibility belongs to the service.
        ShipmentEvent event = buildEvent(OrderStatus.DELIVERED);
        ArgumentCaptor<ShipmentEvent> captor = ArgumentCaptor.forClass(ShipmentEvent.class);

        // Act
        shipmentEventConsumer.consume(event);

        // Assert
        verify(trackingService).recordEvent(captor.capture());
        // occurredAt on the event comes from the payload and must be forwarded as-is
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(event.getOccurredAt());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private ShipmentEvent buildEvent(OrderStatus status) {
        return ShipmentEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .trackingCode("TF-CONS01")
                .status(status)
                .origin("Gdansk")
                .destination("Wroclaw")
                .recipientEmail("consumer@example.com")
                .notes("Consumer test")
                .occurredAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }
}
