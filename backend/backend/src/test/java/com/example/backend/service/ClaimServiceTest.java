package com.example.backend.service;

import com.example.backend.dto.ClaimRequest;
import com.example.backend.dto.ClaimStatusRequest;
import com.example.backend.entity.Claim;
import com.example.backend.entity.ClaimStatus;
import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import com.example.backend.entity.User;
import com.example.backend.exception.DuplicateClaimException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ClaimRepository;
import com.example.backend.repository.ItemRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimServiceTest {

    private ClaimRepository claimRepository;
    private ItemRepository itemRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private ClaimService claimService;
    private Claim pendingClaim;
    private Item foundItem;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        itemRepository = mock(ItemRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        claimService = new ClaimService(claimRepository, itemRepository, userRepository, notificationService);

        foundItem = new Item();
        foundItem.setTitle("Blue wallet");
        foundItem.setType(ItemType.FOUND);
        foundItem.setStatus(ItemStatus.ACTIVE);
        User itemOwner = new User();
        ReflectionTestUtils.setField(itemOwner, "id", 9L);
        foundItem.setCreatedBy(itemOwner);
        ReflectionTestUtils.setField(foundItem, "id", 10L);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(foundItem));

        pendingClaim = new Claim();
        pendingClaim.setItemId(10L);
        pendingClaim.setUserId(7L);
        pendingClaim.setStatus(ClaimStatus.PENDING);
        ReflectionTestUtils.setField(pendingClaim, "id", 20L);
        when(claimRepository.findById(20L)).thenReturn(Optional.of(pendingClaim));

        User user = new User();
        user.setEmail("claimant@example.com");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(userRepository.findByEmail("claimant@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void createsPendingClaimForFoundItem() {
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Claim claim = claimService.submitClaim(request(), "claimant@example.com");

        assertEquals(10L, claim.getItemId());
        assertNull(claim.getLostItemId());
        assertEquals(7L, claim.getUserId());
        assertEquals("I can identify a unique mark.", claim.getVerificationDetails());
        assertEquals(ClaimStatus.PENDING, claim.getStatus());
        verify(claimRepository).save(claim);
        verify(notificationService).createNotification(
                9L,
                "A new claim has been submitted for your found item: Blue wallet",
                "CLAIM_SUBMITTED"
        );
    }

    @Test
    void createsClaimWithValidatedLostItemId() {
        Item lostItem = new Item();
        lostItem.setType(ItemType.LOST);
        ReflectionTestUtils.setField(lostItem, "id", 11L);
        when(itemRepository.findById(11L)).thenReturn(Optional.of(lostItem));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ClaimRequest request = request();
        request.setLostItemId(11L);

        Claim claim = claimService.submitClaim(request, "claimant@example.com");

        assertEquals(10L, claim.getItemId());
        assertEquals(11L, claim.getLostItemId());
    }

    @Test
    void rejectsClaimWithMissingLostItemId() {
        ClaimRequest request = request();
        request.setLostItemId(11L);
        when(itemRepository.findById(11L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> claimService.submitClaim(request, "claimant@example.com"));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void rejectsClaimWhenLostItemIdRefersToFoundItem() {
        Item anotherFoundItem = new Item();
        anotherFoundItem.setType(ItemType.FOUND);
        ReflectionTestUtils.setField(anotherFoundItem, "id", 11L);
        when(itemRepository.findById(11L)).thenReturn(Optional.of(anotherFoundItem));
        ClaimRequest request = request();
        request.setLostItemId(11L);

        assertThrows(IllegalArgumentException.class,
                () -> claimService.submitClaim(request, "claimant@example.com"));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void rejectsClaimForMissingItem() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());
        ClaimRequest request = request();
        request.setItemId(99L);

        assertThrows(ResourceNotFoundException.class,
                () -> claimService.submitClaim(request, "claimant@example.com"));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void rejectsClaimForLostItem() {
        Item lostItem = new Item();
        lostItem.setType(ItemType.LOST);
        ReflectionTestUtils.setField(lostItem, "id", 10L);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(lostItem));

        assertThrows(IllegalArgumentException.class,
                () -> claimService.submitClaim(request(), "claimant@example.com"));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    void preventsDuplicateActiveClaim() {
        when(claimRepository.existsByItemIdAndUserIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(7L),
                anyCollection()))
                .thenReturn(true);

        assertThrows(DuplicateClaimException.class,
                () -> claimService.submitClaim(request(), "claimant@example.com"));
        verify(claimRepository, never()).save(any(Claim.class));
        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void doesNotNotifyClaimantWhenTheyOwnFoundItem() {
        User claimant = new User();
        ReflectionTestUtils.setField(claimant, "id", 7L);
        foundItem.setCreatedBy(claimant);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        claimService.submitClaim(request(), "claimant@example.com");

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void ownerlessLegacyFoundItemStillAcceptsClaimWithoutNotification() {
        foundItem.setCreatedBy(null);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Claim claim = claimService.submitClaim(request(), "claimant@example.com");

        assertEquals(ClaimStatus.PENDING, claim.getStatus());
        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void approvesPendingClaimAndMarksFoundItemClaimed() {
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        pendingClaim.setLostItemId(11L);

        Claim updatedClaim = claimService.updateClaimStatus(20L, statusRequest("APPROVED"));

        assertEquals(ClaimStatus.APPROVED, updatedClaim.getStatus());
        assertEquals(11L, updatedClaim.getLostItemId());
        assertEquals(ItemStatus.CLAIMED, foundItem.getStatus());
        assertEquals(ItemType.FOUND, foundItem.getType());
        verify(itemRepository).save(foundItem);
        verify(claimRepository).save(pendingClaim);
        verify(notificationService).createNotification(
                7L,
                "Your claim for Blue wallet has been approved.",
                "CLAIM_APPROVED"
        );
    }

    @Test
    void rejectsPendingClaimWithoutChangingItemStatus() {
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Claim updatedClaim = claimService.updateClaimStatus(20L, statusRequest("REJECTED"));

        assertEquals(ClaimStatus.REJECTED, updatedClaim.getStatus());
        assertEquals(ItemStatus.ACTIVE, foundItem.getStatus());
        verify(itemRepository, never()).save(any(Item.class));
        verify(claimRepository).save(pendingClaim);
        verify(notificationService).createNotification(
                7L,
                "Your claim for Blue wallet has been rejected.",
                "CLAIM_REJECTED"
        );
    }

    @Test
    void returnsNotFoundForMissingClaim() {
        assertThrows(ResourceNotFoundException.class,
                () -> claimService.updateClaimStatus(999L, statusRequest("APPROVED")));
    }

    @Test
    void rejectsStatusOtherThanApprovedOrRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> claimService.updateClaimStatus(20L, statusRequest("PENDING")));
        verify(claimRepository, never()).save(any(Claim.class));
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void rejectsAlreadyProcessedClaim() {
        pendingClaim.setStatus(ClaimStatus.APPROVED);

        assertThrows(IllegalArgumentException.class,
                () -> claimService.updateClaimStatus(20L, statusRequest("REJECTED")));
        pendingClaim.setStatus(ClaimStatus.REJECTED);
        assertThrows(IllegalArgumentException.class,
                () -> claimService.updateClaimStatus(20L, statusRequest("APPROVED")));
        verify(claimRepository, never()).save(any(Claim.class));
        verify(itemRepository, never()).save(any(Item.class));
    }

    private ClaimRequest request() {
        ClaimRequest request = new ClaimRequest();
        request.setItemId(10L);
        request.setVerificationDetails("  I can identify a unique mark.  ");
        return request;
    }

    private ClaimStatusRequest statusRequest(String status) {
        ClaimStatusRequest request = new ClaimStatusRequest();
        request.setStatus(status);
        return request;
    }
}
