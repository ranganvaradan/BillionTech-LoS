package com.los.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LosJwtServiceTest {

    @Test
    void issueAndVerify_roundTripsRoleAndUid() {
        LosJwtService svc = new LosJwtService(
                new ObjectMapper(),
                "test-hmac-secret-at-least-32-characters-long",
                3600);
        UUID uid = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        String token = svc.issueAccessToken(uid, "CREDIT_MANAGER", "cm@example.com", "CM", null);
        Map<String, Object> claims = svc.verifyAndParse("Bearer " + token);
        assertThat(claims.get("uid")).isEqualTo(uid.toString());
        assertThat(claims.get("role")).isEqualTo("CREDIT_MANAGER");
    }

    @Test
    void shortSecret_notConfigured() {
        LosJwtService svc = new LosJwtService(new ObjectMapper(), "short", 3600);
        assertThat(svc.isConfigured()).isFalse();
        assertThatThrownBy(() -> svc.issueAccessToken(UUID.randomUUID(), "ADMIN", null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
