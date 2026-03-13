package com.trackflow.tracking.messaging;

import com.trackflow.tracking.event.ShipmentEvent;
import com.trackflow.tracking.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventConsumer {

    private final TrackingService trackingService;

    @KafkaListener(topics = "shipment-events", groupId = "tracking-service-group")
    public void consume(ShipmentEvent event) {
        log.info("Received shipment event: orderId={}, trackingCode={}, status={}",
                event.getOrderId(), event.getTrackingCode(), event.getStatus());
        trackingService.recordEvent(event);
    }
}
