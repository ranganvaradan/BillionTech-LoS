package com.los.core.service.credit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ICreditDecisionService {

    CreditDecisionResult evaluate(UUID applicationId);

    record CreditDecisionResult(
            String decision,
            int riskScore,
            int creditScore,
            List<String> reasons,
            List<String> conditions,
            BigDecimal requestedAmount,
            BigDecimal recommendedRate
    ) {}
}
