package com.los.core.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Authenticated password change for a signed-in user (used by the forced first-login reset for
 * auto-provisioned borrowers). The current/temporary password is verified before the change.
 */
@Data
public class ChangePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
