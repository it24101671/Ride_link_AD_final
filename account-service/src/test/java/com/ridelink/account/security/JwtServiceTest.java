package com.ridelink.account.security;

import com.ridelink.account.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", expirationMs);
    }

    @Test
    @DisplayName("Should generate JWT with expected subject, role, and email claims")
    void testGenerateAndExtractClaims() {
        Long userId = 101L;
        String email = "alice@example.com";
        Role role = Role.PASSENGER;

        String token = jwtService.generateToken(userId, email, role);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(email, jwtService.extractEmail(token));
        assertEquals(Role.PASSENGER.name(), jwtService.extractRole(token));
    }

    @Test
    @DisplayName("Should reject invalid or tampered JWT")
    void testRejectInvalidToken() {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.invalidpayload.invalidsignature";
        assertFalse(jwtService.validateToken(invalidToken));
    }

    @Test
    @DisplayName("Should reject expired JWT")
    void testRejectExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1000L); // expired 1s ago
        String token = jwtService.generateToken(1L, "expired@test.com", Role.DRIVER);
        assertFalse(jwtService.validateToken(token));
    }
}
