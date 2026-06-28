package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AnchorRatingTemplateResponse {

    private UUID id;
    private String name;
    private int version;
    private boolean active;
    private Map<String, Object> configJson;
    private Instant createdAt;
    private Instant updatedAt;
}
