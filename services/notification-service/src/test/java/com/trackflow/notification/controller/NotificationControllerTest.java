package com.trackflow.notification.controller;

import com.trackflow.notification.domain.NotificationStatus;
import com.trackflow.notification.domain.OrderStatus;
import com.trackflow.notification.dto.NotificationLogResponse;
import com.trackflow.notification.exception.NotificationNotFoundException;
import com.trackflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    // ─── GET /api/v1/notifications/{orderId} ──────────────────────────────

    @Test
    void should_return200WithLogs_when_logsExistForOrderId() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        NotificationLogResponse response = buildResponse(orderId, NotificationStatus.SENT);
        when(notificationService.getLogs(orderId)).thenReturn(List.of(response));

        // Act & Assert
        mockMvc.perform(get("/api/v1/notifications/{orderId}", orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].notificationStatus").value("SENT"));
    }

    @Test
    void should_return404_when_noLogsFoundForOrderId() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(notificationService.getLogs(orderId))
                .thenThrow(new NotificationNotFoundException("No notifications found for order: " + orderId));

        // Act & Assert
        mockMvc.perform(get("/api/v1/notifications/{orderId}", orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void should_return200WithErrorBody_when_serviceThrowsNotFound() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        String expectedMsg = "No notifications found for order: " + orderId;
        when(notificationService.getLogs(orderId))
                .thenThrow(new NotificationNotFoundException(expectedMsg));

        // Act & Assert
        mockMvc.perform(get("/api/v1/notifications/{orderId}", orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(expectedMsg));
    }

    // ─── GET /api/v1/notifications/failed ─────────────────────────────────

    @Test
    void should_return200WithFailedLogs_when_failedNotificationsExist() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        NotificationLogResponse response = buildResponse(orderId, NotificationStatus.FAILED);
        when(notificationService.getFailedNotifications()).thenReturn(List.of(response));

        // Act & Assert
        mockMvc.perform(get("/api/v1/notifications/failed")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationStatus").value("FAILED"));
    }

    @Test
    void should_return200WithEmptyList_when_noFailedNotificationsExist() throws Exception {
        // Arrange
        when(notificationService.getFailedNotifications()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/v1/notifications/failed")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private NotificationLogResponse buildResponse(UUID orderId, NotificationStatus notifStatus) {
        return NotificationLogResponse.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingCode("TF-TEST01")
                .status(OrderStatus.IN_TRANSIT)
                .recipientEmail("user@example.com")
                .notificationStatus(notifStatus)
                .attemptCount(1)
                .lastAttemptAt(LocalDateTime.now().minusMinutes(5))
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }
}
