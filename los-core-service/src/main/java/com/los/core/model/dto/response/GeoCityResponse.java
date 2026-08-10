package com.los.core.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "India city row for master dropdowns (scoped to parent state)")
public record GeoCityResponse(
        UUID id,
        String cityName,
        String cityCode
) {
}
