package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class WorkflowConfigResponse {

    private UUID id;
    private String name;
    private String borrowerType;
    private String loanProduct;
    private List<Map<String, Object>> steps;
    private boolean active;
    private int version;
    private Instant createdAt;
}
