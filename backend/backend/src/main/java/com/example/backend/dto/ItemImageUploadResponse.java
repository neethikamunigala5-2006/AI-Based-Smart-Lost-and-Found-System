package com.example.backend.dto;

public class ItemImageUploadResponse {

    private final Long itemId;
    private final String imageUrl;
    private final String message;

    public ItemImageUploadResponse(Long itemId, String imageUrl, String message) {
        this.itemId = itemId;
        this.imageUrl = imageUrl;
        this.message = message;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getMessage() {
        return message;
    }
}
