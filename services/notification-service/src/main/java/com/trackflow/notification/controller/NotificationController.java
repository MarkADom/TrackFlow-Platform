package com.trackflow.notification.controller;

import com.trackflow.notification.dto.NotificationLogResponse;
import com.trackflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{orderId}")
    public ResponseEntity<List<NotificationLogResponse>> getLogs(@PathVariable UUID orderId) {
        return ResponseEntity.ok(notificationService.getLogs(orderId));
    }

    @GetMapping("/failed")
    public ResponseEntity<List<NotificationLogResponse>> getFailedNotifications() {
        return ResponseEntity.ok(notificationService.getFailedNotifications());
    }
}
