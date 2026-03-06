package com.trackflow.notification.messaging;

import com.trackflow.notification.event.ShipmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterPublisher {

    private static final String DLQ_TOPIC = "shipment-events.DLQ";

    private final KafkaTemplate<String, ShipmentEvent> kafkaTemplate;

    public void publish(ShipmentEvent event, String reason) {
        log.warn("Publishing to DLQ — orderId={}, trackingCode={}, reason={}",
                event.getOrderId(), event.getTrackingCode(), reason);
        kafkaTemplate.send(DLQ_TOPIC, event.getOrderId().toString(), event);
    }
}
