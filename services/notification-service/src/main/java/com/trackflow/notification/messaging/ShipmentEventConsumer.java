package com.trackflow.notification.messaging;

import com.trackflow.notification.event.ShipmentEvent;
import com.trackflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "shipment-events", groupId = "notification-service-group")
    public void consume(ShipmentEvent event) {
        log.info("Received shipment event: orderId={}, trackingCode={}, status={}",
                event.getOrderId(), event.getTrackingCode(), event.getStatus());
        notificationService.processEvent(event);
    }
}
