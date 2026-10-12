package com.example.backend.security;

import com.example.backend.entity.User;
import com.example.backend.entity.Item;
import com.example.backend.entity.ItemStatus;
import com.example.backend.entity.ItemType;
import com.example.backend.repository.ItemRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.jwt.secret=test-only-secret-with-more-than-32-bytes")
@AutoConfigureMockMvc
class ApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void publicItemListIsAvailableWithoutToken() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk());
    }

    @Test
    void notificationApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications/user/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedItemCreationReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedItemCreationAcceptsValidJwt() throws Exception {
        User user = new User();
        user.setName("Security Test");
        user.setEmail("security-test@example.com");
        user.setPhone("0000000000");
        user.setPassword("test-password-hash");
        user.setRole("USER");
        user = userRepository.save(user);
        String token = jwtService.generateToken(user);
        String requestBody = """
                {
                  "title": "Security test item",
                  "description": "Temporary item for security integration test",
                  "category": "Test",
                  "location": "Test location",
                  "type": "LOST",
                  "contactName": "Security Test",
                  "contactPhone": "0000000000",
                  "createdBy": {"id": 999999}
                }
                """;
        Long createdItemId = null;

        try {
            MvcResult result = mockMvc.perform(post("/api/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();
            Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)")
                    .matcher(result.getResponse().getContentAsString());
            assertTrue(idMatcher.find(), "The created item response should contain its ID");
            createdItemId = Long.parseLong(idMatcher.group(1));
            var createdItem = itemRepository.findById(createdItemId).orElseThrow();
            assertTrue(createdItem.getCreatedBy() != null);
            assertTrue(createdItem.getCreatedBy().getId().equals(user.getId()));
            String responseBody = result.getResponse().getContentAsString();
            assertTrue(!responseBody.contains("createdBy"));
            assertTrue(!responseBody.contains("password"));
        } finally {
            if (createdItemId != null) {
                itemRepository.deleteById(createdItemId);
            }
            userRepository.delete(user);
        }
    }

    @Test
    void adminCanCreateItemWithAuthenticatedOwner() throws Exception {
        User admin = testUser("security-admin@example.com", "ADMIN");
        admin = userRepository.save(admin);
        String token = jwtService.generateToken(admin);
        Long createdItemId = null;

        try {
            MvcResult result = mockMvc.perform(post("/api/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemRequestJson()))
                    .andExpect(status().isCreated())
                    .andReturn();
            Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)")
                    .matcher(result.getResponse().getContentAsString());
            assertTrue(idMatcher.find(), "The created item response should contain its ID");
            createdItemId = Long.parseLong(idMatcher.group(1));
            assertTrue(itemRepository.findById(createdItemId).orElseThrow()
                    .getCreatedBy().getId().equals(admin.getId()));
        } finally {
            if (createdItemId != null) itemRepository.deleteById(createdItemId);
            userRepository.delete(admin);
        }
    }

    @Test
    void existingItemWithNullOwnerCanStillBeRead() throws Exception {
        Item legacyItem = new Item();
        legacyItem.setTitle("Legacy item");
        legacyItem.setDescription("Created before owner tracking");
        legacyItem.setCategory("Other");
        legacyItem.setLocation("Campus");
        legacyItem.setType(ItemType.FOUND);
        legacyItem.setStatus(ItemStatus.ACTIVE);
        legacyItem.setContactName("Legacy reporter");
        legacyItem.setContactPhone("0000000000");
        Item savedItem = itemRepository.save(legacyItem);

        try {
            mockMvc.perform(get("/api/items/" + savedItem.getId()))
                    .andExpect(status().isOk());
            assertTrue(itemRepository.findById(savedItem.getId()).orElseThrow().getCreatedBy() == null);
        } finally {
            itemRepository.deleteById(savedItem.getId());
        }
    }

    @Test
    void protectedItemCreationRejectsInvalidJwt() throws Exception {
        mockMvc.perform(post("/api/items")
                        .header("Authorization", "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private User testUser(String email, String role) {
        User user = new User();
        user.setName("Security Test");
        user.setEmail(email);
        user.setPhone("0000000000");
        user.setPassword("test-password-hash");
        user.setRole(role);
        return user;
    }

    private String itemRequestJson() {
        return """
                {
                  "title": "Security test item",
                  "description": "Temporary item for security integration test",
                  "category": "Test",
                  "location": "Test location",
                  "type": "LOST",
                  "contactName": "Security Test",
                  "contactPhone": "0000000000"
                }
                """;
    }
}
