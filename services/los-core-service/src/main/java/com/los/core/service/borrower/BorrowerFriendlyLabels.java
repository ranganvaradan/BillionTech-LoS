package com.los.core.service.borrower;

import com.los.core.model.enums.ApplicationStatus;

/**
 * Public-facing text only — no internal codes shown to the borrower in API payloads that use this.
 */
public final class BorrowerFriendlyLabels {

    private BorrowerFriendlyLabels() {
    }

    public static String headline(ApplicationStatus s) {
        return headline(s, false);
    }

    public static String headline(ApplicationStatus s, boolean invoiceDiscountingBorrower) {
        if (s == null) {
            return "Processing";
        }
        if (invoiceDiscountingBorrower) {
            return switch (s) {
                case DRAFT -> "Application in progress";
                case CONSENT_PENDING -> "Waiting for your consent";
                case KYC_IN_PROGRESS -> "Verification in progress";
                case KYC_FAILED -> "We need a bit more information";
                case UNDERWRITING, UNDERWRITING_COMPLETED -> "We are reviewing your application";
                case APPROVED, SANCTION_ISSUED, SANCTIONED, KFS_GENERATED -> "Facility approved on agreed terms";
                case REJECTED -> "Application did not go through";
                case CAM_READY -> "We are finalising the credit summary";
                case CAM_REVIEWED, SANCTION_PENDING -> "Final approval in progress";
                case ESIGN_PENDING -> "Terms pending your signature";
                case ESIGN_COMPLETED, READY_FOR_DISBURSEMENT, DISBURSEMENT_PENDING -> "Onboarding complete";
                case DISBURSED -> "Onboarding complete";
                case WITHDRAWN -> "Application withdrawn";
                case ON_HOLD -> "We have paused for now";
            };
        }
        return switch (s) {
            case DRAFT -> "Application in progress";
            case CONSENT_PENDING -> "Waiting for your consent";
            case KYC_IN_PROGRESS -> "Verification in progress";
            case KYC_FAILED -> "We need a bit more information";
            case UNDERWRITING, UNDERWRITING_COMPLETED -> "We are reviewing your application";
            case APPROVED, SANCTION_ISSUED, SANCTIONED, KFS_GENERATED -> "Your loan is approved on agreed terms";
            case REJECTED -> "Application did not go through";
            case CAM_READY -> "We are finalising the credit summary";
            case CAM_REVIEWED, SANCTION_PENDING -> "Final approval in progress";
            case ESIGN_PENDING -> "Agreement pending signature";
            case ESIGN_COMPLETED, READY_FOR_DISBURSEMENT, DISBURSEMENT_PENDING -> "Disbursement in progress";
            case DISBURSED -> "Loan disbursed";
            case WITHDRAWN -> "Application withdrawn";
            case ON_HOLD -> "We have paused for now";
        };
    }

    public static String applicationSummaryStatus(ApplicationStatus s) {
        return headline(s);
    }
}
