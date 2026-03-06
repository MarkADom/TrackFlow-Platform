package com.trackflow.order.service;

import com.trackflow.order.domain.Order;
import com.trackflow.order.domain.OrderStatus;
import com.trackflow.order.dto.CreateOrderRequest;
import com.trackflow.order.dto.OrderResponse;
import com.trackflow.order.dto.UpdateOrderStatusRequest;
import com.trackflow.order.event.ShipmentEvent;
import com.trackflow.order.exception.InvalidStatusTransitionException;
import com.trackflow.order.exception.OrderNotFoundException;
import com.trackflow.order.messaging.OrderEventProducer;
import com.trackflow.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Random RANDOM = new Random();

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String trackingCode = generateTrackingCode();
        Order order = Order.builder()
                .trackingCode(trackingCode)
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .recipientName(request.getRecipientName())
                .recipientEmail(request.getRecipientEmail())
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);
        orderEventProducer.publish(buildEvent(order, null));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        validateStatusTransition(order.getStatus(), request.getStatus());
        order.setStatus(request.getStatus());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        orderEventProducer.publish(buildEvent(order, request.getNotes()));
        return toResponse(order);
    }

    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return toResponse(order);
    }

    public OrderResponse getOrderByTrackingCode(String trackingCode) {
        Order order = orderRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with tracking code: " + trackingCode));
        return toResponse(order);
    }

    private String generateTrackingCode() {
        StringBuilder sb = new StringBuilder("TF-");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (next.ordinal() <= current.ordinal()) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition from " + current + " to " + next);
        }
    }

    private ShipmentEvent buildEvent(Order order, String notes) {
        return ShipmentEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(order.getId())
                .trackingCode(order.getTrackingCode())
                .status(order.getStatus())
                .origin(order.getOrigin())
                .destination(order.getDestination())
                .recipientEmail(order.getRecipientEmail())
                .notes(notes)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .trackingCode(order.getTrackingCode())
                .origin(order.getOrigin())
                .destination(order.getDestination())
                .recipientName(order.getRecipientName())
                .recipientEmail(order.getRecipientEmail())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
