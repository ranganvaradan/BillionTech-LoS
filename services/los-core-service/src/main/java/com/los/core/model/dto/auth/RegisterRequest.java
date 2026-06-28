package com.los.core.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Self-service borrower registration (demo: no real email/SMS delivery).
 */
@Data
public class RegisterRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Email
    @Size(max = 320)
    private String email;

    /** Digits, typically 10 for India — normalized server-side. */
    @NotBlank
    @Size(max = 32)
    private String mobile;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    private String confirmPassword;
}
