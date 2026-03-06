package com.trackflow.tracking.controller;

import com.trackflow.tracking.dto.TrackingEntryResponse;
import com.trackflow.tracking.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @GetMapping("/{orderId}/history")
    public ResponseEntity<List<TrackingEntryResponse>> getHistoryByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(trackingService.getHistoryByOrderId(orderId));
    }

    @GetMapping("/code/{trackingCode}/history")
    public ResponseEntity<List<TrackingEntryResponse>> getHistoryByTrackingCode(
            @PathVariable String trackingCode) {
        return ResponseEntity.ok(trackingService.getHistoryByTrackingCode(trackingCode));
    }

    @GetMapping("/{orderId}/latest")
    public ResponseEntity<TrackingEntryResponse> getLatestStatus(@PathVariable UUID orderId) {
        return ResponseEntity.ok(trackingService.getLatestStatus(orderId));
    }
}
