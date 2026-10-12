package com.example.backend.controller;

import com.example.backend.dto.NotificationResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/user/{userId}")
    public List<NotificationResponse> getNotifications(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return notificationService.getNotifications(resolveAccessibleUserId(userId, authentication));
    }

    @GetMapping("/user/{userId}/unread")
    public List<NotificationResponse> getUnreadNotifications(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return notificationService.getUnreadNotifications(resolveAccessibleUserId(userId, authentication));
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUser(authentication).getId();
        return notificationService.markAsRead(id, userId);
    }

    @PutMapping("/user/{userId}/read-all")
    public List<NotificationResponse> markAllAsRead(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return notificationService.markAllAsRead(resolveAccessibleUserId(userId, authentication));
    }

    private Long resolveAccessibleUserId(Long requestedUserId, Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            return requestedUserId;
        }

        User user = currentUser(authentication);
        if (!user.getId().equals(requestedUserId)) {
            throw new AccessDeniedException("You can only access your own notifications.");
        }
        return user.getId();
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found."));
    }
}
