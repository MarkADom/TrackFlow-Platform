package com.trackflow.order.exception;

import com.trackflow.order.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    // ── OrderNotFoundException → 404 ─────────────────────────────────────────

    @Test
    void should_return404_when_orderNotFoundExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        OrderNotFoundException ex = new OrderNotFoundException("Order not found: 123");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleOrderNotFound(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_includeExceptionMessage_when_orderNotFoundExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        OrderNotFoundException ex = new OrderNotFoundException("Order not found: 123");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleOrderNotFound(ex, request);

        // Assert
        assertThat(response.getBody().getMessage()).isEqualTo("Order not found: 123");
    }

    @Test
    void should_includeRequestPath_when_orderNotFoundExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        OrderNotFoundException ex = new OrderNotFoundException("Order not found: 123");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleOrderNotFound(ex, request);

        // Assert
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/orders/123");
    }

    @Test
    void should_includeStatusCode_when_orderNotFoundExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123");
        OrderNotFoundException ex = new OrderNotFoundException("Order not found: 123");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleOrderNotFound(ex, request);

        // Assert
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    // ── InvalidStatusTransitionException → 400 ───────────────────────────────

    @Test
    void should_return400_when_invalidStatusTransitionExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123/status");
        InvalidStatusTransitionException ex =
                new InvalidStatusTransitionException("Cannot transition from IN_TRANSIT to CREATED");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleInvalidTransition(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_includeExceptionMessage_when_invalidStatusTransitionExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders/123/status");
        InvalidStatusTransitionException ex =
                new InvalidStatusTransitionException("Cannot transition from IN_TRANSIT to CREATED");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleInvalidTransition(ex, request);

        // Assert
        assertThat(response.getBody().getMessage()).contains("Cannot transition from IN_TRANSIT to CREATED");
    }

    // ── MethodArgumentNotValidException → 400 ────────────────────────────────

    @Test
    void should_return400_when_validationFails() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        MethodArgumentNotValidException ex = buildValidationException("origin", "must not be blank");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_includeFieldMessage_when_validationFails() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        MethodArgumentNotValidException ex = buildValidationException("origin", "must not be blank");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getBody().getMessage()).contains("must not be blank");
    }

    @Test
    void should_joinMultipleFieldMessages_when_multipleValidationsFail() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        FieldError error1 = new FieldError("req", "origin", "must not be blank");
        FieldError error2 = new FieldError("req", "destination", "must not be blank");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getBody().getMessage())
                .contains("must not be blank")
                .contains(", ");
    }

    // ── Generic Exception → 500 ───────────────────────────────────────────────

    @Test
    void should_return500_when_unexpectedExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        Exception ex = new RuntimeException("Unexpected error");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void should_returnGenericMessage_when_unexpectedExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        Exception ex = new RuntimeException("Unexpected error");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        // Assert
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void should_notExposeInternalDetails_when_unexpectedExceptionIsThrown() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        Exception ex = new RuntimeException("sensitive internal detail");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        // Assert
        assertThat(response.getBody().getMessage()).doesNotContain("sensitive internal detail");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MethodArgumentNotValidException buildValidationException(String field, String message) {
        FieldError fieldError = new FieldError("createOrderRequest", field, message);
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        return ex;
    }
}
