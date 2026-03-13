package com.trackflow.order.messaging;

import com.trackflow.order.event.ShipmentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private static final String TOPIC = "shipment-events";

    private final KafkaTemplate<String, ShipmentEvent> kafkaTemplate;

    public void publish(ShipmentEvent event) {
        kafkaTemplate.send(TOPIC, event.getOrderId().toString(), event);
    }
}
