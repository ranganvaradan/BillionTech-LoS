package com.los.core.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank
    @Size(max = 64)
    private String token;

    @NotBlank
    @Size(min = 8, max = 200)
    private String newPassword;
}
