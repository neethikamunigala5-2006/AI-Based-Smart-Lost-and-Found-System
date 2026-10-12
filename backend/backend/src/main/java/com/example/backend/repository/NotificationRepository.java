package com.example.backend.repository;

import com.example.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    boolean existsByTypeAndSourceItemIdAndMatchedItemId(String type, Long sourceItemId, Long matchedItemId);
}
