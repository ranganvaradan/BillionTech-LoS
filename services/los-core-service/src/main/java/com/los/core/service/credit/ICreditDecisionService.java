package com.los.core.service.credit;

import java.util.Map;
import java.util.UUID;

public interface ICreditDecisionService {

    CreditDecisionResult evaluate(UUID applicationId);

    record CreditDecisionResult(
            String decision,
            String scoreTier,
            int creditScore,
            double foir,
            Map<String, Object> ruleResults,
            String remarks
    ) {}
}
