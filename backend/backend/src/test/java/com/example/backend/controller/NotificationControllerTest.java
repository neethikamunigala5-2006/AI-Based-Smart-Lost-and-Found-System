package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    @Test
    void regularUserCannotRequestAnotherUsersNotifications() {
        NotificationService notificationService = mock(NotificationService.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User();
        user.setEmail("owner@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 5L);
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        NotificationController controller = new NotificationController(notificationService, userRepository);
        var authentication = new UsernamePasswordAuthenticationToken(
                "owner@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThrows(AccessDeniedException.class, () -> controller.getNotifications(9L, authentication));
        verify(notificationService, never()).getNotifications(9L);
    }

    @Test
    void adminCanRequestAnotherUsersNotifications() {
        NotificationService notificationService = mock(NotificationService.class);
        UserRepository userRepository = mock(UserRepository.class);
        NotificationController controller = new NotificationController(notificationService, userRepository);
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        controller.getNotifications(9L, authentication);

        verify(notificationService).getNotifications(9L);
    }
}
