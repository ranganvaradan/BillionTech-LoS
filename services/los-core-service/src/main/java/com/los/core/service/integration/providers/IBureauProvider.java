package com.los.core.service.integration.providers;

import java.util.Map;

public interface IBureauProvider {

    BureauPullResult pullReport(Map<String, Object> borrowerInfo);

    String getProviderName();

    record BureauPullResult(
            int creditScore,
            String scoreTier,
            int maxDpd12Months,
            int maxDpd24Months,
            int activeAccountCount,
            int recentEnquiryCount,
            Map<String, Object> reportData,
            String rawResponse,
            String transactionId,
            String errorMessage
    ) {}
}
