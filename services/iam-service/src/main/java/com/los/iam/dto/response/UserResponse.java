package com.los.iam.dto.response;

import com.los.iam.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String mobile;
    private Set<Role> roles;
    private boolean enabled;
    private boolean twoFactorEnabled;
    private Instant createdAt;
    private Instant lastLoginAt;
}
