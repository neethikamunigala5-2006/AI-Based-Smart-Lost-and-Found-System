package com.example.backend.service;

import com.example.backend.dto.ItemRequest;
import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import com.example.backend.entity.Claim;
import com.example.backend.entity.ClaimStatus;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ClaimRepository;
import com.example.backend.repository.ItemRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ItemService {

    private final ItemRepository itemRepository;
    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;

    public ItemService(
            ItemRepository itemRepository,
            ClaimRepository claimRepository,
            UserRepository userRepository
    ) {
        this.itemRepository = itemRepository;
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
    }

    public Item createItem(ItemRequest request, String authenticatedEmail) {
        User creator = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found."));
        Item item = new Item();
        copyRequestToItem(request, item);
        item.setStatus(request.getStatus() == null ? ItemStatus.ACTIVE : request.getStatus());
        item.setCreatedBy(creator);
        return itemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Item getItemById(Long id) {
        return findItem(id);
    }

    public Item updateItem(Long id, ItemRequest request) {
        Item item = findItem(id);
        copyRequestToItem(request, item);
        if (request.getStatus() != null) {
            if (request.getStatus() == ItemStatus.RESOLVED) {
                return resolveClaimedItem(item);
            }
            item.setStatus(request.getStatus());
        }
        return itemRepository.save(item);
    }

    public Item markResolved(Long id) {
        return resolveClaimedItem(findItem(id));
    }

    private Item resolveClaimedItem(Item item) {
        if (item.getStatus() != ItemStatus.CLAIMED) {
            throw new IllegalArgumentException("Only CLAIMED items can be marked as RESOLVED.");
        }
        item.setStatus(ItemStatus.RESOLVED);
        Item resolvedFoundItem = itemRepository.save(item);

        if (item.getType() == ItemType.FOUND) {
            List<Claim> approvedClaims =
                    claimRepository.findByItemIdAndStatus(item.getId(), ClaimStatus.APPROVED);
            for (Claim claim : approvedClaims) {
                if (claim.getLostItemId() == null) {
                    continue;
                }
                itemRepository.findById(claim.getLostItemId())
                        .filter(lostItem -> lostItem.getType() == ItemType.LOST)
                        .ifPresent(lostItem -> {
                            lostItem.setStatus(ItemStatus.RESOLVED);
                            itemRepository.save(lostItem);
                        });
            }
        }
        return resolvedFoundItem;
    }

    public void deleteItem(Long id) {
        itemRepository.delete(findItem(id));
    }

    @Transactional(readOnly = true)
    public List<Item> getLostItems() {
        return itemRepository.findByType(ItemType.LOST);
    }

    @Transactional(readOnly = true)
    public List<Item> getFoundItems() {
        return itemRepository.findByType(ItemType.FOUND);
    }

    @Transactional(readOnly = true)
    public List<Item> searchItems(ItemType type, ItemStatus status, String category, String location) {
        return itemRepository.searchItems(type, status, normalize(category), normalize(location));
    }

    private Item findItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item with id " + id + " was not found."));
    }

    private void copyRequestToItem(ItemRequest request, Item item) {
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription().trim());
        item.setCategory(request.getCategory().trim());
        item.setColor(normalize(request.getColor()));
        item.setBrand(normalize(request.getBrand()));
        item.setLocation(request.getLocation().trim());
        item.setDate(request.getDate());
        item.setType(request.getType());
        item.setContactName(request.getContactName().trim());
        item.setContactPhone(request.getContactPhone().trim());
        item.setImageUrl(normalize(request.getImageUrl()));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
