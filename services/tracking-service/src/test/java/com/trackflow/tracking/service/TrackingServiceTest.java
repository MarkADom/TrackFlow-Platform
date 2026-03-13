package com.trackflow.tracking.service;

import com.trackflow.tracking.domain.OrderStatus;
import com.trackflow.tracking.domain.TrackingEntry;
import com.trackflow.tracking.dto.TrackingEntryResponse;
import com.trackflow.tracking.event.ShipmentEvent;
import com.trackflow.tracking.exception.TrackingNotFoundException;
import com.trackflow.tracking.repository.TrackingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private TrackingRepository trackingRepository;

    @InjectMocks
    private TrackingService trackingService;

    // ─── getHistoryByOrderId ───────────────────────────────────────────────

    @Test
    void should_returnHistory_when_entriesExistForOrderId() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        TrackingEntry entry = buildEntry(orderId, "TF-ABC123");
        when(trackingRepository.findByOrderIdOrderByOccurredAtAsc(orderId))
                .thenReturn(List.of(entry));

        // Act
        List<TrackingEntryResponse> result = trackingService.getHistoryByOrderId(orderId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo(orderId);
    }

    @Test
    void should_throwTrackingNotFoundException_when_noEntriesFoundForOrderId() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(trackingRepository.findByOrderIdOrderByOccurredAtAsc(orderId))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertThatThrownBy(() -> trackingService.getHistoryByOrderId(orderId))
                .isInstanceOf(TrackingNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    // ─── getHistoryByTrackingCode ──────────────────────────────────────────

    @Test
    void should_returnHistory_when_entriesExistForTrackingCode() {
        // Arrange
        String trackingCode = "TF-XYZ999";
        TrackingEntry entry = buildEntry(UUID.randomUUID(), trackingCode);
        when(trackingRepository.findByTrackingCodeOrderByOccurredAtAsc(trackingCode))
                .thenReturn(List.of(entry));

        // Act
        List<TrackingEntryResponse> result = trackingService.getHistoryByTrackingCode(trackingCode);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTrackingCode()).isEqualTo(trackingCode);
    }

    @Test
    void should_throwTrackingNotFoundException_when_noEntriesFoundForTrackingCode() {
        // Arrange
        String trackingCode = "TF-NOTFOUND";
        when(trackingRepository.findByTrackingCodeOrderByOccurredAtAsc(trackingCode))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertThatThrownBy(() -> trackingService.getHistoryByTrackingCode(trackingCode))
                .isInstanceOf(TrackingNotFoundException.class)
                .hasMessageContaining(trackingCode);
    }

    // ─── getLatestStatus ──────────────────────────────────────────────────

    @Test
    void should_returnLatestStatus_when_entryExistsForOrderId() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        TrackingEntry entry = buildEntry(orderId, "TF-LATEST");
        when(trackingRepository.findTopByOrderIdOrderByOccurredAtDesc(orderId))
                .thenReturn(Optional.of(entry));

        // Act
        TrackingEntryResponse result = trackingService.getLatestStatus(orderId);

        // Assert
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
    }

    @Test
    void should_throwTrackingNotFoundException_when_noEntryForLatestStatus() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(trackingRepository.findTopByOrderIdOrderByOccurredAtDesc(orderId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> trackingService.getLatestStatus(orderId))
                .isInstanceOf(TrackingNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    // ─── recordEvent ──────────────────────────────────────────────────────

    @Test
    void should_persistEntry_when_validEventReceived() {
        // Arrange
        ShipmentEvent event = buildEvent();

        // Act
        trackingService.recordEvent(event);

        // Assert
        verify(trackingRepository, times(1)).save(any(TrackingEntry.class));
    }

    @Test
    void should_setReceivedAtFromCurrentTime_when_recordingEvent() {
        // Arrange
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ShipmentEvent event = buildEvent();
        ArgumentCaptor<TrackingEntry> captor = ArgumentCaptor.forClass(TrackingEntry.class);

        // Act
        trackingService.recordEvent(event);

        // Assert
        verify(trackingRepository).save(captor.capture());
        TrackingEntry saved = captor.getValue();
        assertThat(saved.getReceivedAt()).isAfterOrEqualTo(before);
        assertThat(saved.getReceivedAt()).isNotEqualTo(saved.getOccurredAt());
    }

    @Test
    void should_mapAllEventFields_when_recordingEvent() {
        // Arrange
        ShipmentEvent event = buildEvent();
        ArgumentCaptor<TrackingEntry> captor = ArgumentCaptor.forClass(TrackingEntry.class);

        // Act
        trackingService.recordEvent(event);

        // Assert
        verify(trackingRepository).save(captor.capture());
        TrackingEntry saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(event.getOrderId());
        assertThat(saved.getTrackingCode()).isEqualTo(event.getTrackingCode());
        assertThat(saved.getStatus()).isEqualTo(event.getStatus());
        assertThat(saved.getOrigin()).isEqualTo(event.getOrigin());
        assertThat(saved.getDestination()).isEqualTo(event.getDestination());
        assertThat(saved.getRecipientEmail()).isEqualTo(event.getRecipientEmail());
        assertThat(saved.getNotes()).isEqualTo(event.getNotes());
        assertThat(saved.getOccurredAt()).isEqualTo(event.getOccurredAt());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private TrackingEntry buildEntry(UUID orderId, String trackingCode) {
        return TrackingEntry.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingCode(trackingCode)
                .status(OrderStatus.IN_TRANSIT)
                .origin("Warsaw")
                .destination("Krakow")
                .recipientEmail("test@example.com")
                .notes("On the way")
                .occurredAt(LocalDateTime.now().minusHours(1))
                .receivedAt(LocalDateTime.now())
                .build();
    }

    private ShipmentEvent buildEvent() {
        return ShipmentEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .trackingCode("TF-EVT001")
                .status(OrderStatus.PICKED_UP)
                .origin("Gdansk")
                .destination("Poznan")
                .recipientEmail("recipient@example.com")
                .notes("Picked up at warehouse")
                .occurredAt(LocalDateTime.now().minusMinutes(30))
                .build();
    }
}
