package com.los.core.service.integration.providers;

import java.util.Map;

public interface IBureauProvider {

    BureauPullResult pullReport(Map<String, Object> borrowerInfo);

    record BureauPullResult(
            boolean success,
            int creditScore,
            Map<String, Object> reportData,
            String transactionId,
            String errorMessage
    ) {}
}
