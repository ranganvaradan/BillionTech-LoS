package com.los.core.model.dto.response;

import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ManualKycDecision;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualKycReviewResponse {

    private UUID id;

    private UUID applicationId;

    private KycStepType stepType;

    private Map<String, Object> data;

    private ManualKycDecision decision;

    private String remarks;

    private UUID reviewedBy;

    private Instant reviewedAt;

    private Instant updatedAt;

    private List<DocumentResponse> documents;
}
