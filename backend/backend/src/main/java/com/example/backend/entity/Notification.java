package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_ai_match_items",
                columnNames = {"type", "source_item_id", "matched_item_id"}
        )
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 100)
    private String type;

    @Column
    private Long sourceItemId;

    @Column
    private Long matchedItemId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification() {
    }

    @PrePersist
    private void setCreationTime() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSourceItemId() {
        return sourceItemId;
    }

    public void setSourceItemId(Long sourceItemId) {
        this.sourceItemId = sourceItemId;
    }

    public Long getMatchedItemId() {
        return matchedItemId;
    }

    public void setMatchedItemId(Long matchedItemId) {
        this.matchedItemId = matchedItemId;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
