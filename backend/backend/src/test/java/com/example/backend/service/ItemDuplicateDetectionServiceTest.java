package com.example.backend.service;

import com.example.backend.dto.DuplicateItemDto;
import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemDuplicateDetectionServiceTest {

    @Test
    void detectsHighSimilarityCandidate() {
        Item source = item(1L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.LOST);
        Item duplicate = item(2L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.LOST);
        ItemRepository repository = repositoryWith(source, List.of(source, duplicate));

        List<DuplicateItemDto> duplicates = service(repository).findDuplicates(1L);

        assertEquals(1, duplicates.size());
        assertEquals(2L, duplicates.get(0).getItemId());
        assertTrue(duplicates.get(0).getSimilarityScore() >= 80);
    }

    @Test
    void excludesLowSimilarityCandidate() {
        Item source = item(1L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.LOST);
        Item unrelated = item(2L, "Red umbrella", "Compact rain umbrella",
                "Umbrella", "Red", "Other", "Gym", LocalDate.of(2025, 1, 1), ItemType.LOST);
        ItemRepository repository = repositoryWith(source, List.of(source, unrelated));

        assertTrue(service(repository).findDuplicates(1L).isEmpty());
    }

    @Test
    void excludesSourceItemFromResults() {
        Item source = item(1L, "Blue wallet", "Blue leather wallet",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.FOUND);
        ItemRepository repository = repositoryWith(source, List.of(source));

        assertTrue(service(repository).findDuplicates(1L).isEmpty());
    }

    @Test
    void sortsCandidatesBySimilarityDescending() {
        Item source = item(1L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.LOST);
        Item closer = item(2L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.LOST);
        Item lessClose = item(3L, "Blue wallet", "Blue wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.LOST);
        ItemRepository repository = repositoryWith(source, List.of(source, lessClose, closer));

        List<DuplicateItemDto> duplicates = service(repository).findDuplicates(1L);

        assertEquals(2, duplicates.size());
        assertEquals(2L, duplicates.get(0).getItemId());
        assertTrue(duplicates.get(0).getSimilarityScore() > duplicates.get(1).getSimilarityScore());
    }

    @Test
    void returnedScoresAreWithinZeroAndOneHundred() {
        Item source = item(1L, "Wallet", "Leather student wallet", "Wallet",
                null, null, "Library", null, ItemType.LOST);
        Item sameType = item(2L, "Wallet", "Leather student wallet", "Wallet",
                null, null, "Library", null, ItemType.LOST);
        Item differentType = item(3L, "Wallet", "Leather student wallet", "Wallet",
                null, null, "Library", null, ItemType.FOUND);
        ItemRepository repository = repositoryWith(source, List.of(source, sameType, differentType));

        List<DuplicateItemDto> duplicates = service(repository).findDuplicates(1L);

        assertTrue(duplicates.stream().allMatch(candidate ->
                candidate.getSimilarityScore() >= 0 && candidate.getSimilarityScore() <= 100));
    }

    @Test
    void missingSourceItemReturnsNotFound() {
        ItemRepository repository = mock(ItemRepository.class);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service(repository).findDuplicates(99L));
    }

    private ItemDuplicateDetectionService service(ItemRepository repository) {
        return new ItemDuplicateDetectionService(
                repository,
                new ItemMatchingService(repository, mock(NotificationService.class)));
    }

    private ItemRepository repositoryWith(Item source, List<Item> allItems) {
        ItemRepository repository = mock(ItemRepository.class);
        when(repository.findById(source.getId())).thenReturn(Optional.of(source));
        when(repository.findAll()).thenReturn(allItems);
        return repository;
    }

    private Item item(
            Long id,
            String title,
            String description,
            String category,
            String color,
            String brand,
            String location,
            LocalDate date,
            ItemType type
    ) {
        Item item = new Item();
        ReflectionTestUtils.setField(item, "id", id);
        item.setTitle(title);
        item.setDescription(description);
        item.setCategory(category);
        item.setColor(color);
        item.setBrand(brand);
        item.setLocation(location);
        item.setDate(date);
        item.setType(type);
        item.setStatus(ItemStatus.ACTIVE);
        item.setContactName("Not compared");
        item.setContactPhone("0000000000");
        return item;
    }
}
