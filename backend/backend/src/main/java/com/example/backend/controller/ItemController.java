package com.example.backend.controller;

import com.example.backend.dto.ItemRequest;
import com.example.backend.dto.ItemMatchResponse;
import com.example.backend.dto.DuplicateItemDto;
import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import com.example.backend.service.ItemMatchingService;
import com.example.backend.service.ItemDuplicateDetectionService;
import com.example.backend.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.backend.dto.ItemImageUploadResponse;
import com.example.backend.service.ItemImageUploadService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "http://localhost:5173")
public class ItemController {

    private final ItemService itemService;
    private final ItemMatchingService itemMatchingService;
    private final ItemDuplicateDetectionService itemDuplicateDetectionService;
    private final ItemImageUploadService itemImageUploadService;

    public ItemController(
            ItemService itemService,
            ItemMatchingService itemMatchingService,
            ItemDuplicateDetectionService itemDuplicateDetectionService,
            ItemImageUploadService itemImageUploadService
    ) {
        this.itemService = itemService;
        this.itemMatchingService = itemMatchingService;
        this.itemDuplicateDetectionService = itemDuplicateDetectionService;
        this.itemImageUploadService = itemImageUploadService;
    }

    @PostMapping
    public ResponseEntity<Item> createItem(
            @Valid @RequestBody ItemRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemService.createItem(request, authentication.getName()));
    }

    @GetMapping
    public List<Item> getAllItems() {
        return itemService.getAllItems();
    }

    @GetMapping("/{id}")
    public Item getItemById(@PathVariable Long id) {
        return itemService.getItemById(id);
    }

    @PutMapping("/{id}")
    public Item updateItem(
            @PathVariable Long id,
            @Valid @RequestBody ItemRequest request,
            Authentication authentication
    ) {
        if (request.getStatus() == ItemStatus.RESOLVED
                && authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Only administrators can mark items as RESOLVED.");
        }
        return itemService.updateItem(id, request);
    }

    @PutMapping("/{id}/resolve")
    public Item markResolved(@PathVariable Long id) {
        return itemService.markResolved(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        itemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lost")
    public List<Item> getLostItems() {
        return itemService.getLostItems();
    }

    @GetMapping("/found")
    public List<Item> getFoundItems() {
        return itemService.getFoundItems();
    }

    @GetMapping("/search")
    public List<Item> searchItems(
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location
    ) {
        return itemService.searchItems(type, status, category, location);
    }

    @GetMapping("/{id}/matches")
    public List<ItemMatchResponse> getMatchesForLostItem(@PathVariable Long id) {
        return itemMatchingService.findMatchesForLostItem(id);
    }

    @GetMapping("/{id}/duplicates")
    public List<DuplicateItemDto> getDuplicates(@PathVariable Long id) {
        return itemDuplicateDetectionService.findDuplicates(id);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemImageUploadResponse uploadImage(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image
    ) throws IOException {
        return itemImageUploadService.uploadImage(id, image);
    }
}
