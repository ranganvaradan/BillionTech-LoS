package com.los.core.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class LoginResponse {
    @JsonProperty("userId")
    UUID userId;
    String name;
    String email;
    String role;
    String institution;
    /** True when the user signed in with a temporary password and must set a new one before continuing. */
    @JsonProperty("passwordResetRequired")
    boolean passwordResetRequired;
}
