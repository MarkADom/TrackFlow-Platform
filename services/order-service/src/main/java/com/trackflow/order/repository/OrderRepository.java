package com.trackflow.order.repository;

import com.trackflow.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByTrackingCode(String trackingCode);
}
