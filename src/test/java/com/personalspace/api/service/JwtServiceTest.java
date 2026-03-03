package com.personalspace.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // 64-byte secret for HS256+
        String secret = "dGVzdFNlY3JldEtleUZvckpXVFRva2VuR2VuZXJhdGlvbjEyMzQ1Njc4OTAxMjM0NTY=";
        jwtService = new JwtService(secret, 900000L);
    }

    @Test
    void generateAccessToken_shouldReturnNonNullToken() {
        String token = jwtService.generateAccessToken("test@test.com");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractEmail_shouldReturnCorrectEmail() {
        String email = "test@test.com";
        String token = jwtService.generateAccessToken(email);
        assertEquals(email, jwtService.extractEmail(token));
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtService.generateAccessToken("test@test.com");
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalseForInvalidToken() {
        assertFalse(jwtService.isTokenValid("invalid.token.here"));
    }

    @Test
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        // Create service with 0ms expiration
        JwtService expiredService = new JwtService(
                "dGVzdFNlY3JldEtleUZvckpXVFRva2VuR2VuZXJhdGlvbjEyMzQ1Njc4OTAxMjM0NTY=", 0L);
        String token = expiredService.generateAccessToken("test@test.com");
        assertFalse(expiredService.isTokenValid(token));
    }

    @Test
    void getAccessTokenExpiration_shouldReturnConfiguredValue() {
        assertEquals(900000L, jwtService.getAccessTokenExpiration());
    }
}
