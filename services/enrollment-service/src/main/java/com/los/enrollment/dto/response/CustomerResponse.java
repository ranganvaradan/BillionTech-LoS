package com.los.enrollment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CustomerResponse {

    private UUID id;
    private String fullName;
    private String mobile;
    private String email;
    private boolean mobileVerified;
    private boolean emailVerified;
    private boolean consentGiven;
    private Instant createdAt;
}
