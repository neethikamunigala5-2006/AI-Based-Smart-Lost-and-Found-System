package com.example.backend.security;

import com.example.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.jwt.secret=test-only-secret-with-more-than-32-bytes")
@AutoConfigureMockMvc
class ClaimApprovalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void regularUserCannotUpdateClaimStatus() throws Exception {
        User user = new User();
        user.setEmail("claim-user-security-test@example.com");
        user.setRole("USER");
        String token = jwtService.generateToken(user);

        mockMvc.perform(put("/api/claims/20/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessClaimStatusOperation() throws Exception {
        User admin = new User();
        admin.setEmail("claim-admin-security-test@example.com");
        admin.setRole("ADMIN");
        String token = jwtService.generateToken(admin);

        mockMvc.perform(put("/api/claims/999999/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}
                                """))
                .andExpect(status().isNotFound());
    }
}
