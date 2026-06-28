package com.los.core.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "India state row for master dropdowns")
public record GeoStateResponse(
        UUID id,
        String stateCode,
        String stateName
) {
}
