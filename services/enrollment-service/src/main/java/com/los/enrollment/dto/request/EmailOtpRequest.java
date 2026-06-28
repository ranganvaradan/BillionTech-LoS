package com.los.enrollment.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * BR-1.5: Email OTP verification request.
 */
@Data
public class EmailOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String otp; // null when sending, present when verifying
}
