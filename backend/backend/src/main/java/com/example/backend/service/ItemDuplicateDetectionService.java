package com.example.backend.service;

import com.example.backend.dto.DuplicateItemDto;
import com.example.backend.entity.Item;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class ItemDuplicateDetectionService {

    private static final int DUPLICATE_THRESHOLD = 80;
    private static final double ITEM_TYPE_WEIGHT = 0.05;

    private final ItemRepository itemRepository;
    private final ItemMatchingService itemMatchingService;

    public ItemDuplicateDetectionService(
            ItemRepository itemRepository,
            ItemMatchingService itemMatchingService
    ) {
        this.itemRepository = itemRepository;
        this.itemMatchingService = itemMatchingService;
    }

    @Transactional(readOnly = true)
    public List<DuplicateItemDto> findDuplicates(Long itemId) {
        Item sourceItem = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item with id " + itemId + " was not found."));

        return itemRepository.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(sourceItem.getId()))
                .map(candidate -> toDuplicate(sourceItem, candidate))
                .filter(candidate -> candidate.getSimilarityScore() >= DUPLICATE_THRESHOLD)
                .sorted(Comparator.comparingInt(DuplicateItemDto::getSimilarityScore).reversed())
                .toList();
    }

    private DuplicateItemDto toDuplicate(Item sourceItem, Item candidate) {
        int fieldScore = itemMatchingService.calculateSimilarityScore(sourceItem, candidate);
        int typeScore = sourceItem.getType() == candidate.getType() ? 100 : 0;
        int score = (int) Math.round(fieldScore * (1.0 - ITEM_TYPE_WEIGHT) + typeScore * ITEM_TYPE_WEIGHT);
        score = Math.max(0, Math.min(100, score));
        return new DuplicateItemDto(candidate, score);
    }
}
