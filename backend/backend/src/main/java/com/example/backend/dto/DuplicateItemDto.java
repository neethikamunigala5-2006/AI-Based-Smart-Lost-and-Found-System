package com.example.backend.dto;

import com.example.backend.entity.Item;
import com.example.backend.entity.ItemType;

import java.time.LocalDate;

public class DuplicateItemDto {

    private final Long itemId;
    private final String title;
    private final ItemType type;
    private final String location;
    private final LocalDate date;
    private final int similarityScore;

    public DuplicateItemDto(Item item, int similarityScore) {
        this.itemId = item.getId();
        this.title = item.getTitle();
        this.type = item.getType();
        this.location = item.getLocation();
        this.date = item.getDate();
        this.similarityScore = similarityScore;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getTitle() {
        return title;
    }

    public ItemType getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getSimilarityScore() {
        return similarityScore;
    }
}
