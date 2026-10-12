package com.example.backend.service;

import com.example.backend.dto.NotificationResponse;
import com.example.backend.entity.Notification;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository);
    }

    @Test
    void createsUnreadNotificationForUser() {
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = service.createNotification(5L, "A claim was updated.", "CLAIM");

        assertEquals("A claim was updated.", response.getMessage());
        assertEquals("CLAIM", response.getType());
        assertFalse(response.isRead());
        verify(repository).save(any(Notification.class));
    }

    @Test
    void retrievesUsersNotificationsNewestFirstRepositoryQuery() {
        Notification notification = notification(10L, 5L, true);
        when(repository.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(notification));

        List<NotificationResponse> responses = service.getNotifications(5L);

        assertEquals(1, responses.size());
        assertEquals(10L, responses.get(0).getId());
        verify(repository).findByUserIdOrderByCreatedAtDesc(5L);
    }

    @Test
    void retrievesUnreadNotificationsOnly() {
        Notification unread = notification(11L, 5L, false);
        when(repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(5L)).thenReturn(List.of(unread));

        List<NotificationResponse> responses = service.getUnreadNotifications(5L);

        assertEquals(1, responses.size());
        assertFalse(responses.get(0).isRead());
        verify(repository).findByUserIdAndReadFalseOrderByCreatedAtDesc(5L);
    }

    @Test
    void marksOneOwnedNotificationAsRead() {
        Notification notification = notification(12L, 5L, false);
        when(repository.findById(12L)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);

        NotificationResponse response = service.markAsRead(12L, 5L);

        assertTrue(response.isRead());
        verify(repository).save(notification);
    }

    @Test
    void rejectsReadingAnotherUsersNotification() {
        Notification notification = notification(13L, 9L, false);
        when(repository.findById(13L)).thenReturn(Optional.of(notification));

        assertThrows(AccessDeniedException.class, () -> service.markAsRead(13L, 5L));
        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void marksAllUsersUnreadNotificationsAsRead() {
        Notification first = notification(14L, 5L, false);
        Notification second = notification(15L, 5L, false);
        when(repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(5L)).thenReturn(List.of(first, second));
        when(repository.saveAll(List.of(first, second))).thenReturn(List.of(first, second));

        List<NotificationResponse> responses = service.markAllAsRead(5L);

        assertEquals(2, responses.size());
        assertTrue(responses.stream().allMatch(NotificationResponse::isRead));
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void reportsMissingNotificationWhenMarkingAsRead() {
        when(repository.findById(20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markAsRead(20L, 5L));
    }

    private Notification notification(Long id, Long userId, boolean read) {
        Notification notification = new Notification();
        ReflectionTestUtils.setField(notification, "id", id);
        notification.setUserId(userId);
        notification.setMessage("Notification message");
        notification.setType("CLAIM");
        notification.setRead(read);
        return notification;
    }
}
