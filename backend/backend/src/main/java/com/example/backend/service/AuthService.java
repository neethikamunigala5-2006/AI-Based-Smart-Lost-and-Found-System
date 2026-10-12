package com.example.backend.service;

import com.example.backend.dto.RegisterRequest;
import com.example.backend.dto.RegisterResponse;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.DuplicateEmailException;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration data is required.");
        }

        String email = request.getEmail().trim();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(DEFAULT_ROLE);

        try {
            return new RegisterResponse(userRepository.save(user));
        } catch (DataIntegrityViolationException exception) {
            if (userRepository.existsByEmail(email)) {
                throw new DuplicateEmailException(email);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(jwtService.generateToken(user), user);
    }
}
