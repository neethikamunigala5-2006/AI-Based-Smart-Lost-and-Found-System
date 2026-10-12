package com.example.backend.service;

import com.example.backend.dto.NotificationResponse;
import com.example.backend.entity.Notification;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.NotificationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public NotificationResponse createNotification(Long userId, String message, String type) {
        if (userId == null || message == null || message.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("A user ID, message, and notification type are required.");
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setMessage(message.trim());
        notification.setType(type.trim());
        notification.setRead(false);
        return toResponse(notificationRepository.save(notification));
    }

    public void createAiMatchNotificationIfAbsent(
            Long userId,
            Long sourceItemId,
            Long matchedItemId,
            String message
    ) {
        if (userId == null || sourceItemId == null || matchedItemId == null
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("AI match notification details are required.");
        }
        if (notificationRepository.existsByTypeAndSourceItemIdAndMatchedItemId(
                "AI_MATCH", sourceItemId, matchedItemId)) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setMessage(message.trim());
        notification.setType("AI_MATCH");
        notification.setSourceItemId(sourceItemId);
        notification.setMatchedItemId(matchedItemId);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::new)
                .toList();
    }

    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification with id " + notificationId + " was not found."));
        verifyOwner(notification, userId);
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    public List<NotificationResponse> markAllAsRead(Long userId) {
        List<Notification> unreadNotifications =
                notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        unreadNotifications.forEach(notification -> notification.setRead(true));
        return notificationRepository.saveAll(unreadNotifications).stream()
                .map(NotificationResponse::new)
                .toList();
    }

    private void verifyOwner(Notification notification, Long userId) {
        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only access your own notifications.");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification);
    }
}
