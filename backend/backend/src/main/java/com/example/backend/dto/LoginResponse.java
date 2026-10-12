package com.example.backend.dto;

import com.example.backend.entity.User;

public class LoginResponse {

    private final String token;
    private final Long id;
    private final String name;
    private final String email;
    private final String role;

    public LoginResponse(String token, User user) {
        this.token = token;
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
    }

    public String getToken() {
        return token;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
