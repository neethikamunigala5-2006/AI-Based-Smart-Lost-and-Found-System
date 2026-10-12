package com.example.backend.dto;

import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;

import java.time.LocalDate;

public class MatchedItemDto {

    private final Long id;
    private final String title;
    private final String description;
    private final String category;
    private final String color;
    private final String brand;
    private final String location;
    private final LocalDate date;
    private final ItemStatus status;
    private final String imageUrl;

    public MatchedItemDto(Item item) {
        this.id = item.getId();
        this.title = item.getTitle();
        this.description = item.getDescription();
        this.category = item.getCategory();
        this.color = item.getColor();
        this.brand = item.getBrand();
        this.location = item.getLocation();
        this.date = item.getDate();
        this.status = item.getStatus();
        this.imageUrl = item.getImageUrl();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getColor() {
        return color;
    }

    public String getBrand() {
        return brand;
    }

    public String getLocation() {
        return location;
    }

    public LocalDate getDate() {
        return date;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
