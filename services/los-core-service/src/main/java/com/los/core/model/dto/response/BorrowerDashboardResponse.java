package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class BorrowerDashboardResponse {
    String fullName;
    String email;
    String mobile;
    int activeLoanCount;
    int draftOrOpenApplicationCount;
    boolean hasIncompleteDraftHint;
    List<BorrowerApplicationSummaryResponse> recentApplications;
    String secondLoanWarning;
    /** First disbursed application id (for post-disbursement loan menu links; demo). */
    UUID primaryDisbursedApplicationId;
}
