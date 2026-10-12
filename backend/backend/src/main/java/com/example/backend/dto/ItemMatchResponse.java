package com.example.backend.dto;

public class ItemMatchResponse {

    private final Long lostItemId;
    private final MatchedItemDto foundItem;
    private final int similarityScore;

    public ItemMatchResponse(Long lostItemId, MatchedItemDto foundItem, int similarityScore) {
        this.lostItemId = lostItemId;
        this.foundItem = foundItem;
        this.similarityScore = similarityScore;
    }

    public Long getLostItemId() {
        return lostItemId;
    }

    public MatchedItemDto getFoundItem() {
        return foundItem;
    }

    public int getSimilarityScore() {
        return similarityScore;
    }
}
