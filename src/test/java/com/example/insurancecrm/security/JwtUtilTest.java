package com.example.insurancecrm.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-must-be-at-least-256-bits-long-for-hmac!!";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60, 30);
    }

    @Test
    void accessToken_carriesEmailUserIdAndRole() {
        String token = jwtUtil.generateAccessToken("agent@test.com", "user-1", "AGENT");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("agent@test.com");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user-1");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("AGENT");
    }

    @Test
    void accessToken_isRecognizedAsAccessTypeNotRefresh() {
        String token = jwtUtil.generateAccessToken("agent@test.com", "user-1", "AGENT");

        assertThat(jwtUtil.isAccessToken(token)).isTrue();
        assertThat(jwtUtil.isRefreshToken(token)).isFalse();
    }

    @Test
    void refreshToken_isRecognizedAsRefreshTypeNotAccess() {
        String token = jwtUtil.generateRefreshToken("agent@test.com", "user-1", "AGENT");

        assertThat(jwtUtil.isRefreshToken(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isFalse();
    }

    @Test
    void isTokenValid_falseForGarbageToken() {
        assertThat(jwtUtil.isTokenValid("not-a-real-jwt")).isFalse();
    }

    @Test
    void isTokenValid_falseForTokenSignedWithDifferentKey() {
        JwtUtil otherIssuer = new JwtUtil("a-completely-different-secret-key-of-sufficient-length-256!", 60, 30);
        String token = otherIssuer.generateAccessToken("agent@test.com", "user-1", "AGENT");

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void expiredToken_isRejectedByAllTypeChecks() {
        // Negative expiry — token is already expired the instant it's minted
        JwtUtil expiredIssuer = new JwtUtil(SECRET, -1, 0);
        String token = expiredIssuer.generateAccessToken("agent@test.com", "user-1", "AGENT");

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
        assertThat(jwtUtil.isAccessToken(token)).isFalse();
        assertThatThrownBy(() -> jwtUtil.extractClaims(token)).isInstanceOf(ExpiredJwtException.class);
    }
}
