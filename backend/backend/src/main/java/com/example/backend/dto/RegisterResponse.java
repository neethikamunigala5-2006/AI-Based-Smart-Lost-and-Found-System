package com.example.backend.dto;

import com.example.backend.entity.User;

public class RegisterResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final String phone;
    private final String role;

    public RegisterResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.role = user.getRole();
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

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }
}
