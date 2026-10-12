package com.example.backend.security;

import com.example.backend.entity.User;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "test-only-signing-key-with-at-least-32-bytes";

    @Test
    void generatedTokenContainsEmailRoleAndExpiration() {
        User user = new User();
        user.setEmail("person@example.com");
        user.setRole("USER");

        String token = new JwtService(TEST_SECRET, 3_600_000).generateToken(user);
        var claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("person@example.com", claims.getSubject());
        assertEquals("USER", claims.get("role"));
        assertNotNull(claims.getExpiration());
    }
}
