package com.los.core.model.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class PhysicalVkycCompletionRequest {
    private String reason;
    private String comments;
    private UUID documentId;
}
