package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LosUserResponse {
    private UUID id;
    private String name;
    private String email;
    private String mobile;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
