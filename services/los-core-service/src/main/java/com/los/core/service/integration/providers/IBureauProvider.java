package com.los.core.service.integration.providers;

import java.util.Map;

public interface IBureauProvider {

    /** Stable name for {@code Map<String, IBureauProvider>} and DB routing. */
    String getProviderName();

    BureauPullResult pullReport(Map<String, Object> borrowerInfo);

    record BureauPullResult(
            boolean success,
            int creditScore,
            Map<String, Object> reportData,
            String transactionId,
            String errorMessage
    ) {}
}
