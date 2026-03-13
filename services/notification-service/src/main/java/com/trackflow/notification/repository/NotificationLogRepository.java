package com.trackflow.notification.repository;

import com.trackflow.notification.domain.NotificationLog;
import com.trackflow.notification.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByOrderId(UUID orderId);

    List<NotificationLog> findByNotificationStatus(NotificationStatus status);
}
