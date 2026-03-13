package com.trackflow.notification.exception;

import com.trackflow.notification.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/notifications/test");
    }

    // ─── NotificationNotFoundException → 404 ─────────────────────────────

    @Test
    void should_return404_when_notificationNotFoundExceptionThrown() {
        // Arrange
        NotificationNotFoundException ex = new NotificationNotFoundException("Notification not found");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotificationNotFound(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_includeExceptionMessage_when_notificationNotFoundExceptionThrown() {
        // Arrange
        NotificationNotFoundException ex = new NotificationNotFoundException("No notifications for order: 123");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotificationNotFound(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("No notifications for order: 123");
    }

    @Test
    void should_includeNotFoundLabel_when_notificationNotFoundExceptionThrown() {
        // Arrange
        NotificationNotFoundException ex = new NotificationNotFoundException("Not found");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotificationNotFound(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    // ─── MethodArgumentNotValidException → 400 ───────────────────────────

    @Test
    void should_return400_when_methodArgumentNotValidExceptionThrown() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("obj", "field", "must not be blank")));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_includeFieldErrorMessages_when_methodArgumentNotValidExceptionThrown() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("obj", "orderId", "must not be null")));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("must not be null");
        assertThat(response.getBody().getError()).isEqualTo("Validation Failed");
    }

    @Test
    void should_joinMultipleFieldErrors_when_severalValidationViolationsPresent() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "field1", "must not be blank"),
                new FieldError("obj", "field2", "must be positive")
        ));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .contains("must not be blank")
                .contains("must be positive");
    }

    // ─── Generic Exception → 500 ──────────────────────────────────────────

    @Test
    void should_return500_when_unexpectedExceptionThrown() {
        // Arrange
        RuntimeException ex = new RuntimeException("Something went wrong");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void should_returnGenericMessage_when_unexpectedExceptionThrown() {
        // Arrange
        RuntimeException ex = new RuntimeException("Internal detail not for client");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getStatus()).isEqualTo(500);
    }

    // ─── Common response fields ───────────────────────────────────────────

    @Test
    void should_includeRequestPath_when_anyExceptionHandled() {
        // Arrange
        NotificationNotFoundException ex = new NotificationNotFoundException("not found");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotificationNotFound(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/notifications/test");
    }

    @Test
    void should_includeTimestamp_when_anyExceptionHandled() {
        // Arrange
        NotificationNotFoundException ex = new NotificationNotFoundException("not found");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotificationNotFound(ex, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}
