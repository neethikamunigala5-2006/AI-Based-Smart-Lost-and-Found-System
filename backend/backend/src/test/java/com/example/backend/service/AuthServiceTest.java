package com.example.backend.service;

import com.example.backend.dto.RegisterRequest;
import com.example.backend.dto.RegisterResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.DuplicateEmailException;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void registersUserWithHashedPasswordAndDefaultRole() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        when(userRepository.existsByEmail("person@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthService authService = new AuthService(userRepository, jwtService);

        RegisterResponse response = authService.register(registerRequest());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertNotEquals("password123", savedUser.getPassword());
        assertTrue(new BCryptPasswordEncoder().matches("password123", savedUser.getPassword()));
        assertEquals("USER", savedUser.getRole());
        assertEquals("person@example.com", response.getEmail());
        assertEquals("USER", response.getRole());
    }

    @Test
    void rejectsAnAlreadyRegisteredEmail() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        when(userRepository.existsByEmail("person@example.com")).thenReturn(true);
        AuthService authService = new AuthService(userRepository, jwtService);

        assertThrows(DuplicateEmailException.class, () -> authService.register(registerRequest()));
    }

    @Test
    void returnsTokenAndSafeUserDetailsForValidLogin() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        User user = registeredUser();
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("signed-token");
        AuthService authService = new AuthService(userRepository, jwtService);

        com.example.backend.dto.LoginRequest request = new com.example.backend.dto.LoginRequest();
        request.setEmail("person@example.com");
        request.setPassword("password123");

        var response = authService.login(request);

        assertEquals("signed-token", response.getToken());
        assertEquals(user.getId(), response.getId());
        assertEquals(user.getName(), response.getName());
        assertEquals(user.getEmail(), response.getEmail());
        assertEquals(user.getRole(), response.getRole());
    }

    @Test
    void rejectsInvalidLoginCredentials() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
        AuthService authService = new AuthService(userRepository, jwtService);

        com.example.backend.dto.LoginRequest request = new com.example.backend.dto.LoginRequest();
        request.setEmail("person@example.com");
        request.setPassword("wrong-password");

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Person");
        request.setEmail("person@example.com");
        request.setPhone("1234567890");
        request.setPassword("password123");
        return request;
    }

    private User registeredUser() {
        User user = new User();
        user.setName("Person");
        user.setEmail("person@example.com");
        user.setPhone("1234567890");
        user.setPassword(new BCryptPasswordEncoder().encode("password123"));
        user.setRole("USER");
        return user;
    }
}
