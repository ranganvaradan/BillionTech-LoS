package com.los.enrollment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * BR-1.4: RM-initiated assisted registration.
 * Allows a Relationship Manager to register a customer on their behalf.
 */
@Data
public class AssistedRegisterRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    private String mobile;

    private String email;

    @NotBlank(message = "RM User ID is required")
    private String rmUserId;

    private String rmBranch;

    private String channel; // BRANCH, DSA, DIGITAL, DIRECT
}
