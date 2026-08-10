package com.los.core.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Demo: reset token and link are included in the JSON so frontend can test without email delivery.
 * Production should not return raw tokens; use a single generic message and email the link.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForgotPasswordResponse {
    String message;
    String resetToken;
    /** App-relative path with query, e.g. /reset-password?token=... */
    String resetPath;
    String resetLink;
    Instant expiresAt;
}
