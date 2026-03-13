package com.trackflow.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.order.domain.OrderStatus;
import com.trackflow.order.dto.CreateOrderRequest;
import com.trackflow.order.dto.OrderResponse;
import com.trackflow.order.dto.UpdateOrderStatusRequest;
import com.trackflow.order.exception.InvalidStatusTransitionException;
import com.trackflow.order.exception.OrderNotFoundException;
import com.trackflow.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    // ── POST /api/v1/orders ───────────────────────────────────────────────────

    @Test
    void should_return201_when_orderCreatedSuccessfully() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(orderService.createOrder(any())).thenReturn(buildResponse(id, OrderStatus.CREATED));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    void should_returnOrderIdInBody_when_orderCreatedSuccessfully() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(orderService.createOrder(any())).thenReturn(buildResponse(id, OrderStatus.CREATED));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest())))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void should_return400_when_originIsMissing() throws Exception {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        request.setOrigin(null);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_destinationIsMissing() throws Exception {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        request.setDestination(null);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_recipientNameIsMissing() throws Exception {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        request.setRecipientName(null);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_recipientEmailIsMissing() throws Exception {
        // Arrange
        CreateOrderRequest request = buildCreateRequest();
        request.setRecipientEmail(null);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/v1/orders/{id}/status ────────────────────────────────────────

    @Test
    void should_return200_when_statusUpdateIsValid() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.PICKED_UP);
        when(orderService.updateStatus(eq(orderId), any()))
                .thenReturn(buildResponse(orderId, OrderStatus.PICKED_UP));

        // Act & Assert
        mockMvc.perform(put("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void should_returnUpdatedStatusInBody_when_statusUpdateIsValid() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.PICKED_UP);
        when(orderService.updateStatus(eq(orderId), any()))
                .thenReturn(buildResponse(orderId, OrderStatus.PICKED_UP));

        // Act & Assert
        mockMvc.perform(put("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void should_return404_when_orderNotFoundOnStatusUpdate() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.PICKED_UP);
        when(orderService.updateStatus(eq(orderId), any()))
                .thenThrow(new OrderNotFoundException("Order not found: " + orderId));

        // Act & Assert
        mockMvc.perform(put("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return400_when_statusTransitionIsInvalid() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = buildStatusRequest(OrderStatus.CREATED);
        when(orderService.updateStatus(eq(orderId), any()))
                .thenThrow(new InvalidStatusTransitionException("Cannot transition from IN_TRANSIT to CREATED"));

        // Act & Assert
        mockMvc.perform(put("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_statusFieldIsNull() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(put("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/orders/{id} ───────────────────────────────────────────────

    @Test
    void should_return200_when_orderFoundById() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(buildResponse(orderId, OrderStatus.CREATED));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk());
    }

    @Test
    void should_returnOrderBodyWithTrackingCode_when_orderFoundById() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(buildResponse(orderId, OrderStatus.CREATED));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(jsonPath("$.trackingCode").value("TF-ABC123"));
    }

    @Test
    void should_return404_when_orderNotFoundById() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId))
                .thenThrow(new OrderNotFoundException("Order not found: " + orderId));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/orders/tracking/{code} ───────────────────────────────────

    @Test
    void should_return200_when_orderFoundByTrackingCode() throws Exception {
        // Arrange
        String code = "TF-ABC123";
        UUID id = UUID.randomUUID();
        when(orderService.getOrderByTrackingCode(code)).thenReturn(buildResponse(id, OrderStatus.CREATED));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/tracking/{code}", code))
                .andExpect(status().isOk());
    }

    @Test
    void should_returnOrderIdInBody_when_orderFoundByTrackingCode() throws Exception {
        // Arrange
        String code = "TF-ABC123";
        UUID id = UUID.randomUUID();
        when(orderService.getOrderByTrackingCode(code)).thenReturn(buildResponse(id, OrderStatus.CREATED));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/tracking/{code}", code))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void should_return404_when_orderNotFoundByTrackingCode() throws Exception {
        // Arrange
        String code = "TF-XXXXXX";
        when(orderService.getOrderByTrackingCode(code))
                .thenThrow(new OrderNotFoundException("Order not found with tracking code: " + code));

        // Act & Assert
        mockMvc.perform(get("/api/v1/orders/tracking/{code}", code))
                .andExpect(status().isNotFound());
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

    private OrderResponse buildResponse(UUID id, OrderStatus status) {
        return OrderResponse.builder()
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
