package com.example.backend.dto;

import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ItemRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must be 150 characters or fewer")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be 2000 characters or fewer")
    private String description;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must be 100 characters or fewer")
    private String category;

    @Size(max = 100, message = "Color must be 100 characters or fewer")
    private String color;

    @Size(max = 100, message = "Brand must be 100 characters or fewer")
    private String brand;

    @NotBlank(message = "Location is required")
    @Size(max = 255, message = "Location must be 255 characters or fewer")
    private String location;

    private LocalDate date;

    @NotNull(message = "Type is required")
    private ItemType type;

    private ItemStatus status;

    @NotBlank(message = "Contact name is required")
    @Size(max = 150, message = "Contact name must be 150 characters or fewer")
    private String contactName;

    @NotBlank(message = "Contact phone is required")
    @Size(max = 40, message = "Contact phone must be 40 characters or fewer")
    private String contactPhone;

    @Size(max = 2048, message = "Image URL must be 2048 characters or fewer")
    private String imageUrl;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
