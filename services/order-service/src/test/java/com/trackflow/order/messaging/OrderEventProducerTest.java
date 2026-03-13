package com.trackflow.order.messaging;

import com.trackflow.order.domain.OrderStatus;
import com.trackflow.order.event.ShipmentEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, ShipmentEvent> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer orderEventProducer;

    @Test
    void should_sendToShipmentEventsTopic_when_eventIsPublished() {
        // Arrange
        ShipmentEvent event = buildEvent();

        // Act
        orderEventProducer.publish(event);

        // Assert
        verify(kafkaTemplate).send(eq("shipment-events"), any(), eq(event));
    }

    @Test
    void should_useOrderIdAsMessageKey_when_eventIsPublished() {
        // Arrange
        ShipmentEvent event = buildEvent();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        orderEventProducer.publish(event);

        // Assert
        verify(kafkaTemplate).send(eq("shipment-events"), keyCaptor.capture(), eq(event));
        assertThat(keyCaptor.getValue()).isEqualTo(event.getOrderId().toString());
    }

    @Test
    void should_sendExactEventAsPayload_when_eventIsPublished() {
        // Arrange
        ShipmentEvent event = buildEvent();
        ArgumentCaptor<ShipmentEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentEvent.class);

        // Act
        orderEventProducer.publish(event);

        // Assert
        verify(kafkaTemplate).send(eq("shipment-events"), any(), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isSameAs(event);
    }

    @Test
    void should_preserveEventStatus_when_eventIsPublished() {
        // Arrange
        ShipmentEvent event = buildEvent();
        ArgumentCaptor<ShipmentEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentEvent.class);

        // Act
        orderEventProducer.publish(event);

        // Assert
        verify(kafkaTemplate).send(eq("shipment-events"), any(), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ShipmentEvent buildEvent() {
        return ShipmentEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .trackingCode("TF-ABC123")
                .status(OrderStatus.IN_TRANSIT)
                .origin("Lisboa")
                .destination("Porto")
                .recipientEmail("joao@example.com")
                .notes("Simulated event")
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
