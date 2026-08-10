package com.los.plp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Mirrors PLP iam-service {@code AuthResponse} JSON for {@code POST /api/v1/auth/login}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpLoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    /** Access token TTL in seconds (PLP defaults to 28800). */
    private long expiresIn;
}
