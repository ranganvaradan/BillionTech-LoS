package com.los.core.model.dto.response;

import com.los.core.model.enums.KycOutcome;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class KycOutcomeResponse {
    private UUID applicationId;
    private KycOutcome outcome;
    private List<Map<String, Object>> stepSummary;
}
