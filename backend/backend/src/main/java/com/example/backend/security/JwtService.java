package com.example.backend.security;

import com.example.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final String secret;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-ms:3600000}") long expirationMs
    ) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        if (expirationMs <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than zero.");
        }

        SecretKey signingKey = getSigningKey();
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set JWT_SECRET to at least 32 bytes.");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
