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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderService orderService;

    // ── createOrder ──────────────────────────────────────────────────────────

    @Test
    void should_returnOrderResponse_when_orderIsCreated() {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        Order saved = buildOrder(UUID.randomUUID(), OrderStatus.CREATED);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertThat(response.getId()).isEqualTo(saved.getId());
    }

    @Test
    void should_setStatusToCreated_when_orderIsCreated() {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        Order saved = buildOrder(UUID.randomUUID(), OrderStatus.CREATED);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void should_generateTrackingCodeWithCorrectFormat_when_orderIsCreated() {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        Order saved = buildOrder(UUID.randomUUID(), OrderStatus.CREATED);
        when(orderRepository.save(captor.capture())).thenReturn(saved);

        // Act
        orderService.createOrder(request);

        // Assert
        assertThat(captor.getValue().getTrackingCode()).matches("TF-[A-Z0-9]{6}");
    }

    @Test
    void should_persistOrderWithRequestFields_when_orderIsCreated() {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        Order saved = buildOrder(UUID.randomUUID(), OrderStatus.CREATED);
        when(orderRepository.save(captor.capture())).thenReturn(saved);

        // Act
        orderService.createOrder(request);

        // Assert
        Order persisted = captor.getValue();
        assertThat(persisted.getOrigin()).isEqualTo("Lisboa");
        assertThat(persisted.getDestination()).isEqualTo("Porto");
        assertThat(persisted.getRecipientName()).isEqualTo("João Silva");
        assertThat(persisted.getRecipientEmail()).isEqualTo("joao@example.com");
    }

    @Test
    void should_publishEvent_when_orderIsCreated() {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        Order saved = buildOrder(UUID.randomUUID(), OrderStatus.CREATED);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        // Act
        orderService.createOrder(request);

        // Assert
        verify(orderEventProducer).publish(any(ShipmentEvent.class));
    }

    @Test
    void should_publishEventWithCreatedStatus_when_orderIsCreated() {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        Order saved = buildOrder(UUID.randomUUID(), OrderStatus.CREATED);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);
        ArgumentCaptor<ShipmentEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentEvent.class);

        // Act
        orderService.createOrder(request);

        // Assert
        verify(orderEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    // ── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void should_returnResponseWithNewStatus_when_statusTransitionIsValid() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order existing = buildOrder(orderId, OrderStatus.CREATED);
        Order updated = buildOrder(orderId, OrderStatus.PICKED_UP);
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.PICKED_UP);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenReturn(updated);

        // Act
        OrderResponse response = orderService.updateStatus(orderId, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
    }

    @Test
    void should_publishEvent_when_statusTransitionIsValid() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order existing = buildOrder(orderId, OrderStatus.CREATED);
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.PICKED_UP);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenReturn(existing);

        // Act
        orderService.updateStatus(orderId, request);

        // Assert
        verify(orderEventProducer).publish(any(ShipmentEvent.class));
    }

    @Test
    void should_publishEventWithNotes_when_statusTransitionIncludesNotes() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order existing = buildOrder(orderId, OrderStatus.CREATED);
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.PICKED_UP);
        request.setNotes("Picked up at warehouse");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenReturn(existing);
        ArgumentCaptor<ShipmentEvent> eventCaptor = ArgumentCaptor.forClass(ShipmentEvent.class);

        // Act
        orderService.updateStatus(orderId, request);

        // Assert
        verify(orderEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getNotes()).isEqualTo("Picked up at warehouse");
    }

    @Test
    void should_throwInvalidStatusTransitionException_when_transitionIsBackward() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order existing = buildOrder(orderId, OrderStatus.IN_TRANSIT);
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.CREATED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> orderService.updateStatus(orderId, request))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot transition from IN_TRANSIT to CREATED");
    }

    @Test
    void should_throwInvalidStatusTransitionException_when_transitionIsToSameStatus() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order existing = buildOrder(orderId, OrderStatus.IN_TRANSIT);
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.IN_TRANSIT);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> orderService.updateStatus(orderId, request))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void should_allowTransitionToFailed_when_orderIsInTransit() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order existing = buildOrder(orderId, OrderStatus.IN_TRANSIT);
        Order updated = buildOrder(orderId, OrderStatus.FAILED);
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.FAILED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenReturn(updated);

        // Act
        OrderResponse response = orderService.updateStatus(orderId, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void should_allowTransitionToFailed_when_orderIsCreated() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order existing = buildOrder(orderId, OrderStatus.CREATED);
        Order updated = buildOrder(orderId, OrderStatus.FAILED);
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.FAILED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenReturn(updated);

        // Act
        OrderResponse response = orderService.updateStatus(orderId, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void should_throwOrderNotFoundException_when_orderDoesNotExistOnUpdate() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.PICKED_UP);

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.updateStatus(orderId, request))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void should_returnOrderResponse_when_orderExistsById() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, OrderStatus.CREATED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Act
        OrderResponse response = orderService.getOrder(orderId);

        // Assert
        assertThat(response.getId()).isEqualTo(orderId);
    }

    @Test
    void should_mapAllFields_when_orderExistsById() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, OrderStatus.IN_TRANSIT);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Act
        OrderResponse response = orderService.getOrder(orderId);

        // Assert
        assertThat(response.getTrackingCode()).isEqualTo("TF-ABC123");
        assertThat(response.getOrigin()).isEqualTo("Lisboa");
        assertThat(response.getDestination()).isEqualTo("Porto");
        assertThat(response.getRecipientName()).isEqualTo("João Silva");
        assertThat(response.getRecipientEmail()).isEqualTo("joao@example.com");
    }

    @Test
    void should_throwOrderNotFoundException_when_orderDoesNotExistById() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    // ── getOrderByTrackingCode ────────────────────────────────────────────────

    @Test
    void should_returnOrderResponse_when_trackingCodeExists() {
        // Arrange
        String trackingCode = "TF-ABC123";
        Order order = buildOrder(UUID.randomUUID(), OrderStatus.CREATED);
        when(orderRepository.findByTrackingCode(trackingCode)).thenReturn(Optional.of(order));

        // Act
        OrderResponse response = orderService.getOrderByTrackingCode(trackingCode);

        // Assert
        assertThat(response.getTrackingCode()).isEqualTo(trackingCode);
    }

    @Test
    void should_throwOrderNotFoundException_when_trackingCodeDoesNotExist() {
        // Arrange
        String trackingCode = "TF-XXXXXX";
        when(orderRepository.findByTrackingCode(trackingCode)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrderByTrackingCode(trackingCode))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(trackingCode);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateOrderRequest buildCreateRequest() {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setOrigin("Lisboa");
        r.setDestination("Porto");
        r.setRecipientName("João Silva");
        r.setRecipientEmail("joao@example.com");
        return r;
    }

    private UpdateOrderStatusRequest buildStatusRequest(OrderStatus status) {
        UpdateOrderStatusRequest r = new UpdateOrderStatusRequest();
        r.setStatus(status);
        return r;
    }

    private Order buildOrder(UUID id, OrderStatus status) {
        return Order.builder()
                .id(id)
                .trackingCode("TF-ABC123")
                .origin("Lisboa")
                .destination("Porto")
                .recipientName("João Silva")
                .recipientEmail("joao@example.com")
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
