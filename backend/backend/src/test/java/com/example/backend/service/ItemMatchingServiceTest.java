package com.example.backend.service;

import com.example.backend.dto.ItemMatchResponse;
import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import com.example.backend.entity.User;
import com.example.backend.repository.ItemRepository;
import com.example.backend.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemMatchingServiceTest {

    @Test
    void returnsPositiveMatchesInDescendingScoreOrderWithinRange() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        Item lostItem = item("Blue campus wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.LOST);
        Item strongMatch = item("Blue wallet", "Blue leather wallet found with card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 6), ItemType.FOUND);
        Item weakerMatch = item("Wallet found", "Wallet with student card",
                "Wallet", null, null, "Gym", null, ItemType.FOUND);
        Item unrelatedItem = item("Red umbrella", "Red umbrella found",
                "Umbrella", "Red", null, "Gym", null, ItemType.FOUND);

        when(itemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(itemRepository.findByType(ItemType.FOUND))
                .thenReturn(List.of(weakerMatch, unrelatedItem, strongMatch));

        List<ItemMatchResponse> matches = new ItemMatchingService(
                itemRepository, mock(NotificationService.class)).findMatchesForLostItem(1L);

        assertEquals(2, matches.size());
        assertTrue(matches.get(0).getSimilarityScore() > matches.get(1).getSimilarityScore());
        assertTrue(matches.stream().allMatch(match ->
                match.getSimilarityScore() >= 0 && match.getSimilarityScore() <= 100));
    }

    @Test
    void strongMatchCreatesAiMatchNotificationForLostItemOwner() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        Item lostItem = item(1L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.LOST);
        Item foundItem = item(2L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.FOUND);
        setOwner(lostItem, 7L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(itemRepository.findByType(ItemType.FOUND)).thenReturn(List.of(foundItem));
        when(notificationRepository.existsByTypeAndSourceItemIdAndMatchedItemId("AI_MATCH", 1L, 2L))
                .thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ItemMatchResponse> matches = matchingService(itemRepository, notificationRepository)
                .findMatchesForLostItem(1L);

        assertEquals(1, matches.size());
        assertTrue(matches.get(0).getSimilarityScore() >= 80);
        verify(notificationRepository).save(any());
    }

    @Test
    void weakMatchDoesNotCreateAiMatchNotification() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        Item lostItem = item(1L, "Blue campus wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.LOST);
        Item unrelatedFoundItem = item(2L, "Red umbrella", "Compact rain umbrella",
                "Umbrella", "Red", "Other", "Gym", LocalDate.of(2025, 1, 1), ItemType.FOUND);
        setOwner(lostItem, 7L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(itemRepository.findByType(ItemType.FOUND)).thenReturn(List.of(unrelatedFoundItem));

        matchingService(itemRepository, notificationRepository).findMatchesForLostItem(1L);

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never())
                .existsByTypeAndSourceItemIdAndMatchedItemId(any(), any(), any());
    }

    @Test
    void repeatedMatchRequestDoesNotCreateDuplicateAiMatchNotification() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        Item lostItem = item(1L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.LOST);
        Item foundItem = item(2L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.FOUND);
        setOwner(lostItem, 7L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(itemRepository.findByType(ItemType.FOUND)).thenReturn(List.of(foundItem));
        when(notificationRepository.existsByTypeAndSourceItemIdAndMatchedItemId("AI_MATCH", 1L, 2L))
                .thenReturn(false, true);
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ItemMatchingService service = matchingService(itemRepository, notificationRepository);

        service.findMatchesForLostItem(1L);
        service.findMatchesForLostItem(1L);

        verify(notificationRepository, org.mockito.Mockito.times(2))
                .existsByTypeAndSourceItemIdAndMatchedItemId("AI_MATCH", 1L, 2L);
        verify(notificationRepository).save(any());
    }

    @Test
    void ownerlessLostItemDoesNotCreateAiMatchNotification() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        Item lostItem = item(1L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.LOST);
        Item foundItem = item(2L, "Blue wallet", "Blue leather wallet with student card",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.FOUND);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(itemRepository.findByType(ItemType.FOUND)).thenReturn(List.of(foundItem));

        matchingService(itemRepository, notificationRepository).findMatchesForLostItem(1L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void foundSourceItemDoesNotGenerateAiMatchNotification() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        Item foundItem = item(1L, "Blue wallet", "Blue leather wallet",
                "Wallet", "Blue", "Acme", "Library", LocalDate.of(2026, 10, 5), ItemType.FOUND);
        setOwner(foundItem, 7L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(foundItem));

        assertThrows(IllegalArgumentException.class,
                () -> matchingService(itemRepository, notificationRepository).findMatchesForLostItem(1L));
        verify(notificationRepository, never()).save(any());
    }

    private ItemMatchingService matchingService(
            ItemRepository itemRepository,
            NotificationRepository notificationRepository
    ) {
        return new ItemMatchingService(itemRepository, new NotificationService(notificationRepository));
    }

    private void setOwner(Item item, Long userId) {
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", userId);
        item.setCreatedBy(owner);
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
        Item item = item(title, description, category, color, brand, location, date, type);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Item item(
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
        item.setTitle(title);
        item.setDescription(description);
        item.setCategory(category);
        item.setColor(color);
        item.setBrand(brand);
        item.setLocation(location);
        item.setDate(date);
        item.setType(type);
        item.setStatus(ItemStatus.ACTIVE);
        item.setContactName("Test");
        item.setContactPhone("0000000000");
        return item;
    }
}
