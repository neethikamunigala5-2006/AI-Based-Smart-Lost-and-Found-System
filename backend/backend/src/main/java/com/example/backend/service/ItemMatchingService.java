package com.example.backend.service;

import com.example.backend.dto.ItemMatchResponse;
import com.example.backend.dto.MatchedItemDto;
import com.example.backend.entity.Item;
import com.example.backend.entity.ItemType;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ItemMatchingService {

    private static final int TITLE_WEIGHT = 25;
    private static final int DESCRIPTION_WEIGHT = 20;
    private static final int CATEGORY_WEIGHT = 20;
    private static final int COLOR_WEIGHT = 10;
    private static final int BRAND_WEIGHT = 10;
    private static final int LOCATION_WEIGHT = 10;
    private static final int DATE_WEIGHT = 5;

    private final ItemRepository itemRepository;
    private final NotificationService notificationService;

    public ItemMatchingService(ItemRepository itemRepository, NotificationService notificationService) {
        this.itemRepository = itemRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<ItemMatchResponse> findMatchesForLostItem(Long lostItemId) {
        Item lostItem = itemRepository.findById(lostItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item with id " + lostItemId + " was not found."));

        if (lostItem.getType() != ItemType.LOST) {
            throw new IllegalArgumentException("Matches can only be requested for LOST items.");
        }

        List<ItemMatchResponse> matches = itemRepository.findByType(ItemType.FOUND).stream()
                .map(foundItem -> createMatch(lostItem, foundItem))
                .filter(match -> match.getSimilarityScore() > 0)
                .sorted((first, second) ->
                        Integer.compare(second.getSimilarityScore(), first.getSimilarityScore()))
                .toList();

        if (lostItem.getCreatedBy() != null) {
            Long ownerId = lostItem.getCreatedBy().getId();
            matches.stream()
                    .filter(match -> match.getSimilarityScore() >= 80)
                    .forEach(match -> {
                        Long matchedItemId = match.getFoundItem().getId();
                        String message = "AI found a possible match for your lost item: "
                                + match.getFoundItem().getTitle()
                                + " (" + match.getSimilarityScore() + "% similarity).";
                        notificationService.createAiMatchNotificationIfAbsent(
                                ownerId,
                                lostItemId,
                                matchedItemId,
                                message
                        );
                    });
        }
        return matches;
    }

    private ItemMatchResponse createMatch(Item lostItem, Item foundItem) {
        int similarityScore = calculateSimilarityScore(lostItem, foundItem);
        return new ItemMatchResponse(
                lostItem.getId(),
                new MatchedItemDto(foundItem),
                similarityScore
        );
    }

    public int calculateSimilarityScore(Item firstItem, Item secondItem) {
        int availableWeight = 0;
        double weightedScore = 0;

        int titleScore = textSimilarity(firstItem.getTitle(), secondItem.getTitle());
        if (isAvailable(firstItem.getTitle(), secondItem.getTitle())) {
            weightedScore += titleScore * TITLE_WEIGHT;
            availableWeight += TITLE_WEIGHT;
        }

        int descriptionScore = textSimilarity(firstItem.getDescription(), secondItem.getDescription());
        if (isAvailable(firstItem.getDescription(), secondItem.getDescription())) {
            weightedScore += descriptionScore * DESCRIPTION_WEIGHT;
            availableWeight += DESCRIPTION_WEIGHT;
        }

        int categoryScore = textSimilarity(firstItem.getCategory(), secondItem.getCategory());
        if (isAvailable(firstItem.getCategory(), secondItem.getCategory())) {
            weightedScore += categoryScore * CATEGORY_WEIGHT;
            availableWeight += CATEGORY_WEIGHT;
        }

        int colorScore = textSimilarity(firstItem.getColor(), secondItem.getColor());
        if (isAvailable(firstItem.getColor(), secondItem.getColor())) {
            weightedScore += colorScore * COLOR_WEIGHT;
            availableWeight += COLOR_WEIGHT;
        }

        int brandScore = textSimilarity(firstItem.getBrand(), secondItem.getBrand());
        if (isAvailable(firstItem.getBrand(), secondItem.getBrand())) {
            weightedScore += brandScore * BRAND_WEIGHT;
            availableWeight += BRAND_WEIGHT;
        }

        int locationScore = textSimilarity(firstItem.getLocation(), secondItem.getLocation());
        if (isAvailable(firstItem.getLocation(), secondItem.getLocation())) {
            weightedScore += locationScore * LOCATION_WEIGHT;
            availableWeight += LOCATION_WEIGHT;
        }

        if (firstItem.getDate() != null && secondItem.getDate() != null) {
            long daysApart = Math.abs(ChronoUnit.DAYS.between(firstItem.getDate(), secondItem.getDate()));
            double dateScore = Math.max(0.0, 1.0 - daysApart / 30.0) * 100;
            weightedScore += dateScore * DATE_WEIGHT;
            availableWeight += DATE_WEIGHT;
        }

        return availableWeight == 0
                ? 0
                : (int) Math.round(weightedScore / availableWeight);
    }

    private int textSimilarity(String first, String second) {
        if (!isAvailable(first, second)) {
            return 0;
        }

        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);
        Set<String> sharedWords = new HashSet<>(firstWords);
        sharedWords.retainAll(secondWords);

        Set<String> allWords = new HashSet<>(firstWords);
        allWords.addAll(secondWords);

        return allWords.isEmpty() ? 0 : (int) Math.round((double) sharedWords.size() / allWords.size() * 100);
    }

    private Set<String> words(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean isAvailable(String first, String second) {
        return first != null && !first.isBlank() && second != null && !second.isBlank();
    }
}
