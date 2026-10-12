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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ClaimService {

    private static final List<ClaimStatus> ACTIVE_STATUSES =
            List.of(ClaimStatus.PENDING, ClaimStatus.APPROVED);

    private final ClaimRepository claimRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ClaimService(
            ClaimRepository claimRepository,
            ItemRepository itemRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.claimRepository = claimRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public Claim submitClaim(ClaimRequest request, String authenticatedEmail) {
        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item with id " + request.getItemId() + " was not found."));
        if (item.getType() != ItemType.FOUND) {
            throw new IllegalArgumentException("Claims can only be submitted for FOUND items.");
        }

        Item lostItem = null;
        if (request.getLostItemId() != null) {
            lostItem = itemRepository.findById(request.getLostItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Lost item with id " + request.getLostItemId() + " was not found."));
            if (lostItem.getType() != ItemType.LOST) {
                throw new IllegalArgumentException("lostItemId must refer to a LOST item.");
            }
        }

        User user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found."));

        if (claimRepository.existsByItemIdAndUserIdAndStatusIn(
                item.getId(), user.getId(), ACTIVE_STATUSES)) {
            throw new DuplicateClaimException("You already have an active claim for this item.");
        }

        Claim claim = new Claim();
        claim.setItemId(item.getId());
        claim.setLostItemId(lostItem == null ? null : lostItem.getId());
        claim.setUserId(user.getId());
        claim.setVerificationDetails(request.getVerificationDetails().trim());
        claim.setStatus(ClaimStatus.PENDING);
        Claim savedClaim = claimRepository.save(claim);
        User itemOwner = item.getCreatedBy();
        if (itemOwner != null && !itemOwner.getId().equals(user.getId())) {
            notificationService.createNotification(
                    itemOwner.getId(),
                    "A new claim has been submitted for your found item: " + item.getTitle(),
                    "CLAIM_SUBMITTED"
            );
        }
        return savedClaim;
    }

    @Transactional(readOnly = true)
    public List<Claim> getClaimsForItem(Long itemId) {
        return claimRepository.findByItemIdOrderByCreatedAtDesc(itemId);
    }

    @Transactional(readOnly = true)
    public List<Claim> getClaimsForUser(Long userId) {
        return claimRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Claim getClaimById(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim with id " + id + " was not found."));
    }

    public Claim updateClaimStatus(Long id, ClaimStatusRequest request) {
        Claim claim = getClaimById(id);
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING claims can be approved or rejected.");
        }

        ClaimStatus requestedStatus;
        try {
            requestedStatus = ClaimStatus.valueOf(request.getStatus().trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Claim status must be APPROVED or REJECTED.");
        }
        if (requestedStatus != ClaimStatus.APPROVED && requestedStatus != ClaimStatus.REJECTED) {
            throw new IllegalArgumentException("Claim status must be APPROVED or REJECTED.");
        }

        Item item = itemRepository.findById(claim.getItemId()).orElse(null);
        if (requestedStatus == ClaimStatus.APPROVED) {
            if (item == null) {
                throw new ResourceNotFoundException(
                        "Item with id " + claim.getItemId() + " was not found.");
            }
            if (item.getType() != ItemType.FOUND) {
                throw new IllegalArgumentException("Only FOUND items can be claimed.");
            }
            item.setStatus(ItemStatus.CLAIMED);
            itemRepository.save(item);
        }

        claim.setStatus(requestedStatus);
        Claim updatedClaim = claimRepository.save(claim);
        String itemTitle = item == null ? "item " + claim.getItemId() : item.getTitle();
        String notificationType = requestedStatus == ClaimStatus.APPROVED
                ? "CLAIM_APPROVED"
                : "CLAIM_REJECTED";
        String notificationMessage = requestedStatus == ClaimStatus.APPROVED
                ? "Your claim for " + itemTitle + " has been approved."
                : "Your claim for " + itemTitle + " has been rejected.";
        notificationService.createNotification(claim.getUserId(), notificationMessage, notificationType);
        return updatedClaim;
    }
}
