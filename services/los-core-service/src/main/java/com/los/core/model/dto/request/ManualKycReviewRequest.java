package com.los.core.model.dto.request;

import lombok.*;

import com.los.core.model.enums.ManualKycDecision;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualKycReviewRequest {

    private Map<String, Object> data;

    private String remarks;

    private ManualKycDecision decision;

    private List<UUID> documentIds;
}
