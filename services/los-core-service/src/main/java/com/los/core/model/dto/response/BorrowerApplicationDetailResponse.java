package com.los.core.model.dto.response;

import com.los.core.model.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Borrower-safe application detail: no underwriting score, CAM, internal rules, or audit.
 */
@Value
@Builder
public class BorrowerApplicationDetailResponse {
    UUID applicationId;
    String applicationNumber;
    String customerName;
    String product;
    ApplicationStatus status;
    String friendlyStatusHeadline;
    String currentStageMessage;
    String estimatedProcessingHint;
    List<String> requiredActions;
    String kycStatus;
    String documentStatus;
    String sanctionStatus;
    String kfsStatus;
    String eSignStatus;
    String disbursementStatus;
    String rejectionMessage;
    boolean reapplyVisible;
    BigDecimal disbursedAmount;
    Instant disbursedAt;
    String disbursementAccountMask;
    String loanAccountNumber;
    List<BorrowerTimelineStepResponse> timeline;
    /** Borrower-submitted collateral (intake) — no internal credit remarks. */
    List<BorrowerLabelValueItem> collateralSummary;
    /** Invoice discounting borrower onboarding — no term loan or KFS tab in portal. */
    boolean invoiceDiscountingBorrower;
    BigDecimal sanctionedAmount;
    BigDecimal interestRate;
    Integer tenureMonths;
    boolean termsDocumentAvailable;
}
