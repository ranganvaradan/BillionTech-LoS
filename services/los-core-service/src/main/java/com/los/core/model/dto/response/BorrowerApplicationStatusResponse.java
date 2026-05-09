package com.los.core.model.dto.response;

import com.los.core.model.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Borrower-safe projection for a shareable status page. Excludes credit scores, CAM, rules, and internal remarks.
 */
@Value
@Builder
public class BorrowerApplicationStatusResponse {

    UUID applicationId;
    String applicationNumber;
    String customerName;
    String product;
    ApplicationStatus status;
    List<String> requiredActions;
    String kycStatus;
    String documentStatus;
    String sanctionStatus;
    String kfsStatus;
    String eSignStatus;
    String disbursementStatus;
}
