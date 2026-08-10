package com.los.core.service.borrower;

import com.los.core.model.dto.response.BorrowerApplicationStatusResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps application state to borrower-facing labels. Does not read underwriting, CAM, or rule outputs.
 */
@Service
public class BorrowerApplicationStatusService {

    public BorrowerApplicationStatusResponse build(LoanApplication app, boolean documentChecklistComplete, int documentCount) {
        ApplicationStatus s = app.getStatus();
        String name = firstNonBlank(
                str(mapGet(app.getPersonalInfo(), "fullName")),
                str(mapGet(app.getPersonalInfo(), "name")),
                "Applicant");

        return BorrowerApplicationStatusResponse.builder()
                .applicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .customerName(name)
                .product(app.getLoanProduct())
                .status(s)
                .requiredActions(deriveRequiredActions(s, documentChecklistComplete, documentCount))
                .kycStatus(kycLabel(s))
                .documentStatus(documentLabel(s, documentChecklistComplete, documentCount))
                .sanctionStatus(sanctionLabel(s))
                .kfsStatus(kfsLabel(s))
                .eSignStatus(esignLabel(s, app.getEsignTransactionId() != null))
                .disbursementStatus(disbursementLabel(s, app.getDisbursedAt() != null))
                .build();
    }

    private static List<String> deriveRequiredActions(
            ApplicationStatus s, boolean documentChecklistComplete, int documentCount) {
        List<String> out = new ArrayList<>();
        if (s == ApplicationStatus.DRAFT) {
            if (documentCount == 0) {
                out.add("Upload the documents we need to continue.");
            } else if (!documentChecklistComplete) {
                out.add("Add any remaining documents to complete the checklist.");
            }
            out.add("Review your details and submit the application for verification.");
        } else if (s == ApplicationStatus.CONSENT_PENDING) {
            out.add("Please complete consent to continue.");
        } else if (s == ApplicationStatus.KYC_IN_PROGRESS) {
            out.add("Complete verification if we have sent you a link or instructions.");
        } else if (s == ApplicationStatus.KYC_FAILED) {
            out.add("Additional information may be required — we will contact you or update this page.");
        } else if (s == ApplicationStatus.ESIGN_PENDING) {
            out.add("Sign the agreement and related documents we sent to you.");
        } else if (s == ApplicationStatus.READY_FOR_DISBURSEMENT
                || s == ApplicationStatus.DISBURSEMENT_PENDING) {
            out.add("Disbursement is being arranged.");
        } else if (s == ApplicationStatus.SANCTION_PENDING) {
            out.add("Your application is with the credit team for approval.");
        }
        return out;
    }

    private static String kycLabel(ApplicationStatus s) {
        return switch (s) {
            case DRAFT, CONSENT_PENDING, BORROWER_SENT_BACK -> "Not started";
            case BORROWER_SUBMITTED, PENDING_CREDIT_OFFICER, SENT_BACK_TO_RM ->
                    "Submitted — awaiting lender review";
            case KYC_IN_PROGRESS -> "Verification in progress";
            case KYC_FAILED -> "Verification could not be completed";
            case REJECTED, WITHDRAWN -> "Closed";
            default -> "Verification complete";
        };
    }

    private static String documentLabel(ApplicationStatus s, boolean checklistComplete, int documentCount) {
        if (s == ApplicationStatus.DRAFT) {
            if (documentCount == 0) {
                return "Documents pending";
            }
            if (!checklistComplete) {
                return "Some documents may still be required";
            }
            return "Documents received";
        }
        if (s.compareTo(ApplicationStatus.UNDERWRITING) < 0
                && s != ApplicationStatus.CONSENT_PENDING
                && s != ApplicationStatus.BORROWER_SENT_BACK
                && s != ApplicationStatus.BORROWER_SUBMITTED
                && s != ApplicationStatus.PENDING_CREDIT_OFFICER
                && s != ApplicationStatus.SENT_BACK_TO_RM) {
            return documentCount == 0 ? "Documents pending" : "Under review with your file";
        }
        return "With your application file";
    }

    private static String sanctionLabel(ApplicationStatus s) {
        return switch (s) {
            case DRAFT, CONSENT_PENDING, BORROWER_SUBMITTED, PENDING_CREDIT_OFFICER, SENT_BACK_TO_RM,
                    BORROWER_SENT_BACK, KYC_IN_PROGRESS, KYC_FAILED,
                    UNDERWRITING, UNDERWRITING_COMPLETED, CAM_READY, CAM_SENT_BACK, CAM_REVIEWED -> "Pending";
            case REJECTED, WITHDRAWN -> "Not applicable";
            case SANCTION_PENDING -> "In review";
            case SANCTIONED, KFS_GENERATED, SANCTION_ISSUED, ESIGN_PENDING, ESIGN_COMPLETED, APPROVED, READY_FOR_DISBURSEMENT,
                    DISBURSEMENT_PENDING, DISBURSED, ON_HOLD -> "Completed";
        };
    }

    private static String kfsLabel(ApplicationStatus s) {
        return switch (s) {
            case DRAFT, CONSENT_PENDING, BORROWER_SUBMITTED, PENDING_CREDIT_OFFICER, SENT_BACK_TO_RM,
                    BORROWER_SENT_BACK, KYC_IN_PROGRESS, KYC_FAILED,
                    UNDERWRITING, UNDERWRITING_COMPLETED, CAM_READY, CAM_SENT_BACK, CAM_REVIEWED, SANCTION_PENDING, SANCTIONED,
                    APPROVED -> "Pending";
            case REJECTED, WITHDRAWN -> "Not applicable";
            case KFS_GENERATED, ESIGN_PENDING, ESIGN_COMPLETED, READY_FOR_DISBURSEMENT, DISBURSEMENT_PENDING, DISBURSED,
                    SANCTION_ISSUED, ON_HOLD -> "Available";
        };
    }

    private static String esignLabel(ApplicationStatus s, boolean hasEsignTransaction) {
        if (s == ApplicationStatus.REJECTED || s == ApplicationStatus.WITHDRAWN) {
            return "Not applicable";
        }
        if (s == ApplicationStatus.DRAFT
                || s == ApplicationStatus.CONSENT_PENDING
                || s == ApplicationStatus.BORROWER_SENT_BACK) {
            return "Not started";
        }
        if (s == ApplicationStatus.KYC_IN_PROGRESS
                || s == ApplicationStatus.KYC_FAILED
                || s.compareTo(ApplicationStatus.ESIGN_PENDING) < 0) {
            return "Not yet required";
        }
        if (s == ApplicationStatus.ESIGN_PENDING) {
            return "Signature requested" + (hasEsignTransaction ? " (in progress)" : "");
        }
        return s.compareTo(ApplicationStatus.ESIGN_COMPLETED) >= 0 ? "Signed" : "Not yet required";
    }

    private static String disbursementLabel(ApplicationStatus s, boolean disbursed) {
        if (disbursed || s == ApplicationStatus.DISBURSED) {
            return "Disbursed";
        }
        if (s == ApplicationStatus.DISBURSEMENT_PENDING) {
            return "Disbursement in progress";
        }
        if (s == ApplicationStatus.READY_FOR_DISBURSEMENT) {
            return "Ready to disburse";
        }
        if (s == ApplicationStatus.ESIGN_PENDING || s == ApplicationStatus.ESIGN_COMPLETED) {
            return "After agreement is signed";
        }
        return "Not started";
    }

    private static Object mapGet(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        return map.get(key);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static String firstNonBlank(String... s) {
        for (String x : s) {
            if (x != null && !x.isBlank()) {
                return x;
            }
        }
        return s.length > 0 && s[0] != null ? s[0] : "Applicant";
    }
}
