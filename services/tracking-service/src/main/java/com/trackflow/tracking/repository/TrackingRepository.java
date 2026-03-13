package com.trackflow.tracking.repository;

import com.trackflow.tracking.domain.TrackingEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackingRepository extends JpaRepository<TrackingEntry, UUID> {

    List<TrackingEntry> findByOrderIdOrderByOccurredAtAsc(UUID orderId);

    List<TrackingEntry> findByTrackingCodeOrderByOccurredAtAsc(String trackingCode);

    Optional<TrackingEntry> findTopByOrderIdOrderByOccurredAtDesc(UUID orderId);
}
