package com.trackflow.tracking.controller;

import com.trackflow.tracking.domain.OrderStatus;
import com.trackflow.tracking.dto.TrackingEntryResponse;
import com.trackflow.tracking.exception.TrackingNotFoundException;
import com.trackflow.tracking.service.TrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TrackingController.class)
class TrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingService trackingService;

    // ─── GET /{orderId}/history ───────────────────────────────────────────

    @Test
    void should_return200WithHistory_when_entriesExistForOrderId() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        TrackingEntryResponse entry = buildResponse(orderId, "TF-ABC123");
        when(trackingService.getHistoryByOrderId(orderId)).thenReturn(List.of(entry));

        // Act & Assert
        mockMvc.perform(get("/api/v1/tracking/{orderId}/history", orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].trackingCode").value("TF-ABC123"));
    }

    @Test
    void should_return404_when_noHistoryFoundForOrderId() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(trackingService.getHistoryByOrderId(orderId))
                .thenThrow(new TrackingNotFoundException("No tracking history found for order: " + orderId));

        // Act & Assert
        mockMvc.perform(get("/api/v1/tracking/{orderId}/history", orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ─── GET /code/{trackingCode}/history ─────────────────────────────────

    @Test
    void should_return200WithHistory_when_entriesExistForTrackingCode() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        String trackingCode = "TF-XYZ999";
        TrackingEntryResponse entry = buildResponse(orderId, trackingCode);
        when(trackingService.getHistoryByTrackingCode(trackingCode)).thenReturn(List.of(entry));

        // Act & Assert
        mockMvc.perform(get("/api/v1/tracking/code/{trackingCode}/history", trackingCode)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingCode").value(trackingCode));
    }

    @Test
    void should_return404_when_noHistoryFoundForTrackingCode() throws Exception {
        // Arrange
        String trackingCode = "TF-MISSING";
        when(trackingService.getHistoryByTrackingCode(trackingCode))
                .thenThrow(new TrackingNotFoundException("No tracking history found for code: " + trackingCode));

        // Act & Assert
        mockMvc.perform(get("/api/v1/tracking/code/{trackingCode}/history", trackingCode)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ─── GET /{orderId}/latest ────────────────────────────────────────────

    @Test
    void should_return200WithLatestStatus_when_entryExistsForOrderId() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        TrackingEntryResponse entry = buildResponse(orderId, "TF-LATE01");
        when(trackingService.getLatestStatus(orderId)).thenReturn(entry);

        // Act & Assert
        mockMvc.perform(get("/api/v1/tracking/{orderId}/latest", orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    void should_return404_when_noLatestStatusFoundForOrderId() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(trackingService.getLatestStatus(orderId))
                .thenThrow(new TrackingNotFoundException("No tracking entries found for order: " + orderId));

        // Act & Assert
        mockMvc.perform(get("/api/v1/tracking/{orderId}/latest", orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private TrackingEntryResponse buildResponse(UUID orderId, String trackingCode) {
        return TrackingEntryResponse.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingCode(trackingCode)
                .status(OrderStatus.IN_TRANSIT)
                .origin("Warsaw")
                .destination("Krakow")
                .recipientEmail("user@example.com")
                .notes("In transit")
                .occurredAt(LocalDateTime.now().minusHours(2))
                .receivedAt(LocalDateTime.now().minusHours(2))
                .build();
    }
}
