package com.trackflow.tracking.service;

import com.trackflow.tracking.domain.TrackingEntry;
import com.trackflow.tracking.dto.TrackingEntryResponse;
import com.trackflow.tracking.event.ShipmentEvent;
import com.trackflow.tracking.exception.TrackingNotFoundException;
import com.trackflow.tracking.repository.TrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final TrackingRepository trackingRepository;

    @Transactional
    public void recordEvent(ShipmentEvent event) {
        TrackingEntry entry = TrackingEntry.builder()
                .orderId(event.getOrderId())
                .trackingCode(event.getTrackingCode())
                .status(event.getStatus())
                .origin(event.getOrigin())
                .destination(event.getDestination())
                .recipientEmail(event.getRecipientEmail())
                .notes(event.getNotes())
                .occurredAt(event.getOccurredAt())
                .receivedAt(LocalDateTime.now())
                .build();
        trackingRepository.save(entry);
    }

    public List<TrackingEntryResponse> getHistoryByOrderId(UUID orderId) {
        List<TrackingEntry> entries = trackingRepository.findByOrderIdOrderByOccurredAtAsc(orderId);
        if (entries.isEmpty()) {
            throw new TrackingNotFoundException("No tracking history found for order: " + orderId);
        }
        return entries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<TrackingEntryResponse> getHistoryByTrackingCode(String trackingCode) {
        List<TrackingEntry> entries = trackingRepository.findByTrackingCodeOrderByOccurredAtAsc(trackingCode);
        if (entries.isEmpty()) {
            throw new TrackingNotFoundException("No tracking history found for code: " + trackingCode);
        }
        return entries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TrackingEntryResponse getLatestStatus(UUID orderId) {
        return trackingRepository.findTopByOrderIdOrderByOccurredAtDesc(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new TrackingNotFoundException("No tracking entries found for order: " + orderId));
    }

    private TrackingEntryResponse toResponse(TrackingEntry entry) {
        return TrackingEntryResponse.builder()
                .id(entry.getId())
                .orderId(entry.getOrderId())
                .trackingCode(entry.getTrackingCode())
                .status(entry.getStatus())
                .origin(entry.getOrigin())
                .destination(entry.getDestination())
                .recipientEmail(entry.getRecipientEmail())
                .notes(entry.getNotes())
                .occurredAt(entry.getOccurredAt())
                .receivedAt(entry.getReceivedAt())
                .build();
    }
}
