package com.example.backend.dto;

import com.example.backend.entity.Notification;

import java.time.LocalDateTime;

public class NotificationResponse {

    private final Long id;
    private final String message;
    private final String type;
    private final boolean read;
    private final LocalDateTime createdAt;

    public NotificationResponse(Notification notification) {
        this.id = notification.getId();
        this.message = notification.getMessage();
        this.type = notification.getType();
        this.read = notification.isRead();
        this.createdAt = notification.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    public boolean isRead() {
        return read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
