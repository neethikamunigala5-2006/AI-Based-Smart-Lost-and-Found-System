package com.example.backend.security;

import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import com.example.backend.entity.Claim;
import com.example.backend.entity.ClaimStatus;
import com.example.backend.entity.User;
import com.example.backend.repository.ClaimRepository;
import com.example.backend.repository.ItemRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.jwt.secret=test-only-secret-with-more-than-32-bytes")
@AutoConfigureMockMvc
class ItemResolutionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    private final List<Long> createdItemIds = new ArrayList<>();
    private final List<Long> createdClaimIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        claimRepository.deleteAllById(createdClaimIds);
        itemRepository.deleteAllById(createdItemIds);
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void adminCanResolveClaimedItem() throws Exception {
        Item item = saveItem(ItemStatus.CLAIMED);

        mockMvc.perform(put("/api/items/" + item.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertEquals(ItemStatus.RESOLVED, itemRepository.findById(item.getId()).orElseThrow().getStatus());
    }

    @Test
    void resolvingFoundItemAlsoResolvesLinkedLostItemForApprovedClaim() throws Exception {
        Item foundItem = saveItem(ItemStatus.CLAIMED, ItemType.FOUND);
        Item lostItem = saveItem(ItemStatus.ACTIVE, ItemType.LOST);
        saveApprovedClaim(foundItem.getId(), lostItem.getId());

        mockMvc.perform(put("/api/items/" + foundItem.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertEquals(ItemStatus.RESOLVED,
                itemRepository.findById(foundItem.getId()).orElseThrow().getStatus());
        assertEquals(ItemStatus.RESOLVED,
                itemRepository.findById(lostItem.getId()).orElseThrow().getStatus());
    }

    @Test
    void aiMatchClaimIsLinkedThroughApprovalAndResolutionWorkflow() throws Exception {
        Item foundItem = saveItem(ItemStatus.ACTIVE, ItemType.FOUND);
        Item lostItem = saveItem(ItemStatus.ACTIVE, ItemType.LOST);
        User claimant = saveUser("resolution-claimant-" + System.nanoTime() + "@example.com");
        String claimantToken = jwtService.generateToken(claimant);

        var claimResult = mockMvc.perform(post("/api/claims")
                        .header("Authorization", "Bearer " + claimantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "itemId": %d,
                                  "lostItemId": %d,
                                  "verificationDetails": "I can verify this item."
                                }
                                """.formatted(foundItem.getId(), lostItem.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemId").value(foundItem.getId()))
                .andExpect(jsonPath("$.lostItemId").value(lostItem.getId()))
                .andReturn();

        Long claimId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(claimResult.getResponse().getContentAsString())
                .get("id")
                .asLong();
        createdClaimIds.add(claimId);

        mockMvc.perform(get("/api/claims/item/" + foundItem.getId())
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lostItemId").value(lostItem.getId()));

        mockMvc.perform(put("/api/claims/" + claimId + "/status")
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lostItemId").value(lostItem.getId()));

        assertEquals(ItemStatus.CLAIMED,
                itemRepository.findById(foundItem.getId()).orElseThrow().getStatus());
        assertEquals(ItemStatus.ACTIVE,
                itemRepository.findById(lostItem.getId()).orElseThrow().getStatus());

        mockMvc.perform(put("/api/items/" + foundItem.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertEquals(ItemStatus.RESOLVED,
                itemRepository.findById(foundItem.getId()).orElseThrow().getStatus());
        assertEquals(ItemStatus.RESOLVED,
                itemRepository.findById(lostItem.getId()).orElseThrow().getStatus());
    }

    @Test
    void resolvingLegacyClaimWithoutLostItemIdOnlyResolvesFoundItem() throws Exception {
        Item foundItem = saveItem(ItemStatus.CLAIMED, ItemType.FOUND);
        Item unrelatedLostItem = saveItem(ItemStatus.ACTIVE, ItemType.LOST);
        saveApprovedClaim(foundItem.getId(), null);

        mockMvc.perform(put("/api/items/" + foundItem.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk());

        assertEquals(ItemStatus.RESOLVED,
                itemRepository.findById(foundItem.getId()).orElseThrow().getStatus());
        assertEquals(ItemStatus.ACTIVE,
                itemRepository.findById(unrelatedLostItem.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminStatusUpdateAlsoResolvesLinkedLostItem() throws Exception {
        Item foundItem = saveItem(ItemStatus.CLAIMED, ItemType.FOUND);
        Item lostItem = saveItem(ItemStatus.ACTIVE, ItemType.LOST);
        saveApprovedClaim(foundItem.getId(), lostItem.getId());

        mockMvc.perform(put("/api/items/" + foundItem.getId())
                        .header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Resolution test item",
                                  "description": "Temporary item for resolution workflow test",
                                  "category": "Other",
                                  "location": "Campus",
                                  "type": "FOUND",
                                  "status": "RESOLVED",
                                  "contactName": "Reporter",
                                  "contactPhone": "0000000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertEquals(ItemStatus.RESOLVED,
                itemRepository.findById(lostItem.getId()).orElseThrow().getStatus());
    }

    @Test
    void activeItemCannotBeResolved() throws Exception {
        Item item = saveItem(ItemStatus.ACTIVE);

        mockMvc.perform(put("/api/items/" + item.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isBadRequest());

        assertEquals(ItemStatus.ACTIVE, itemRepository.findById(item.getId()).orElseThrow().getStatus());
    }

    @Test
    void missingItemReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/items/999999/resolve")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void regularUserCannotResolveClaimedItem() throws Exception {
        Item item = saveItem(ItemStatus.CLAIMED);

        mockMvc.perform(put("/api/items/" + item.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isForbidden());

        assertEquals(ItemStatus.CLAIMED, itemRepository.findById(item.getId()).orElseThrow().getStatus());
    }

    @Test
    void unauthenticatedUserCannotResolveItem() throws Exception {
        mockMvc.perform(put("/api/items/1/resolve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotSetResolvedStatusThroughExistingUpdateEndpoint() throws Exception {
        Item item = saveItem(ItemStatus.CLAIMED);

        mockMvc.perform(put("/api/items/" + item.getId())
                        .header("Authorization", "Bearer " + token("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Test item",
                                  "description": "Resolution permission test",
                                  "category": "Other",
                                  "location": "Campus",
                                  "type": "FOUND",
                                  "status": "RESOLVED",
                                  "contactName": "Reporter",
                                  "contactPhone": "0000000000"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertEquals(ItemStatus.CLAIMED, itemRepository.findById(item.getId()).orElseThrow().getStatus());
    }

    private Item saveItem(ItemStatus status) {
        return saveItem(status, ItemType.FOUND);
    }

    private Item saveItem(ItemStatus status, ItemType type) {
        Item item = new Item();
        item.setTitle("Resolution test item");
        item.setDescription("Temporary item for resolution workflow test");
        item.setCategory("Other");
        item.setLocation("Campus");
        item.setType(type);
        item.setStatus(status);
        item.setContactName("Reporter");
        item.setContactPhone("0000000000");
        Item savedItem = itemRepository.save(item);
        createdItemIds.add(savedItem.getId());
        return savedItem;
    }

    private void saveApprovedClaim(Long foundItemId, Long lostItemId) {
        Claim claim = new Claim();
        claim.setItemId(foundItemId);
        claim.setLostItemId(lostItemId);
        claim.setUserId(7L);
        claim.setVerificationDetails("Verified for resolution test");
        claim.setStatus(ClaimStatus.APPROVED);
        Claim savedClaim = claimRepository.save(claim);
        createdClaimIds.add(savedClaim.getId());
    }

    private User saveUser(String email) {
        User user = new User();
        user.setName("Resolution workflow claimant");
        user.setEmail(email);
        user.setPhone("0000000000");
        user.setPassword("test-password-hash");
        user.setRole("USER");
        User savedUser = userRepository.save(user);
        createdUserIds.add(savedUser.getId());
        return savedUser;
    }

    private String token(String role) {
        User user = new User();
        user.setEmail("item-resolution-" + role.toLowerCase() + "@example.com");
        user.setRole(role);
        return jwtService.generateToken(user);
    }
}
