package com.los.core.service.borrower;

import com.los.core.model.dto.response.BorrowerTimelineStepResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Coarse business-friendly lifecycle for the portal — not tied to internal workflow step names.
 */
@Service
public class BorrowerLifecycleTimelineService {

    public List<BorrowerTimelineStepResponse> build(LoanApplication app) {
        ApplicationStatus s = app.getStatus();
        List<BorrowerTimelineStepResponse> out = new ArrayList<>();

        out.add(step("submitted", "Application submitted", descSubmit(s),
                stateAfterDraft(s), ts(s != ApplicationStatus.DRAFT, app)));

        out.add(step("docs", "Document verification", "We verify identity, address, and income documents.",
                stateKycPhase(s), ts(pastKyc(s), app)));

        out.add(step("credit", "Credit check and underwriting", "We assess eligibility and the loan offer.",
                stateCredit(s), ts(pastCredit(s), app)));

        out.add(step("outcome", "Approval decision", "You are informed of approval, a revised offer, or decline.",
                stateOutcome(s), ts(decisionKnown(s), app)));

        out.add(step("kfs", "KFS and agreement", "Review the Key Fact Statement and sign the loan agreement digitally.",
                stateKfs(s), ts(pastEsign(s), app)));

        out.add(step("disb", "Loan disbursement", "Loan amount is credited to your bank after all checks.",
                stateDisb(s, app), app.getDisbursedAt()));

        return out;
    }

    private BorrowerTimelineStepResponse step(
            String id, String label, String desc, String state, Instant completedAt) {
        return BorrowerTimelineStepResponse.builder()
                .id(id)
                .label(label)
                .state(state)
                .description(desc)
                .completedAt(completedAt)
                .build();
    }

    private String descSubmit(ApplicationStatus s) {
        if (s == ApplicationStatus.DRAFT) {
            return "Finish and submit your application to start processing.";
        }
        return "We received your application.";
    }

    private String stateAfterDraft(ApplicationStatus s) {
        if (s == ApplicationStatus.DRAFT) {
            return "in_progress";
        }
        if (s == ApplicationStatus.REJECTED || s == ApplicationStatus.WITHDRAWN) {
            return "locked";
        }
        return "completed";
    }

    private String stateKycPhase(ApplicationStatus s) {
        if (s == ApplicationStatus.REJECTED || s == ApplicationStatus.WITHDRAWN) {
            return "locked";
        }
        if (s == ApplicationStatus.DRAFT || s == ApplicationStatus.CONSENT_PENDING) {
            return "pending";
        }
        if (s == ApplicationStatus.KYC_IN_PROGRESS || s == ApplicationStatus.KYC_FAILED) {
            return "in_progress";
        }
        if (pastKyc(s)) {
            return "completed";
        }
        return "pending";
    }

    private boolean pastKyc(ApplicationStatus s) {
        return s != ApplicationStatus.DRAFT
                && s != ApplicationStatus.CONSENT_PENDING
                && s != ApplicationStatus.KYC_IN_PROGRESS
                && s != ApplicationStatus.KYC_FAILED;
    }

    private String stateCredit(ApplicationStatus s) {
        if (s == ApplicationStatus.REJECTED || s == ApplicationStatus.WITHDRAWN) {
            return "locked";
        }
        if (!pastKyc(s)) {
            return "pending";
        }
        if (s == ApplicationStatus.UNDERWRITING
                || s == ApplicationStatus.UNDERWRITING_COMPLETED
                || s == ApplicationStatus.CAM_READY
                || s == ApplicationStatus.CAM_REVIEWED) {
            return "in_progress";
        }
        if (pastCredit(s)) {
            return "completed";
        }
        return "pending";
    }

    private boolean pastCredit(ApplicationStatus s) {
        if (s == ApplicationStatus.REJECTED) {
            return true;
        }
        return s == ApplicationStatus.SANCTIONED
                || s == ApplicationStatus.KFS_GENERATED
                || s == ApplicationStatus.ESIGN_PENDING
                || s == ApplicationStatus.ESIGN_COMPLETED
                || s == ApplicationStatus.READY_FOR_DISBURSEMENT
                || s == ApplicationStatus.DISBURSEMENT_PENDING
                || s == ApplicationStatus.DISBURSED
                || s == ApplicationStatus.APPROVED
                || s == ApplicationStatus.SANCTION_ISSUED
                || s == ApplicationStatus.ON_HOLD;
    }

    private String stateOutcome(ApplicationStatus s) {
        if (s == ApplicationStatus.REJECTED) {
            return "completed";
        }
        if (s == ApplicationStatus.WITHDRAWN) {
            return "locked";
        }
        if (!pastCredit(s)) {
            return "pending";
        }
        if (s == ApplicationStatus.SANCTION_PENDING) {
            return "in_progress";
        }
        if (decisionKnown(s)) {
            return "completed";
        }
        return "pending";
    }

    private boolean decisionKnown(ApplicationStatus s) {
        return s == ApplicationStatus.SANCTIONED
                || s == ApplicationStatus.KFS_GENERATED
                || s == ApplicationStatus.ESIGN_PENDING
                || s == ApplicationStatus.ESIGN_COMPLETED
                || s == ApplicationStatus.READY_FOR_DISBURSEMENT
                || s == ApplicationStatus.DISBURSEMENT_PENDING
                || s == ApplicationStatus.DISBURSED
                || s == ApplicationStatus.APPROVED
                || s == ApplicationStatus.SANCTION_ISSUED;
    }

    private String stateKfs(ApplicationStatus s) {
        if (s == ApplicationStatus.REJECTED || s == ApplicationStatus.WITHDRAWN) {
            return "locked";
        }
        if (!decisionKnown(s) || s == ApplicationStatus.SANCTION_PENDING) {
            return "pending";
        }
        if (s == ApplicationStatus.KFS_GENERATED || s == ApplicationStatus.ESIGN_PENDING) {
            return "in_progress";
        }
        if (pastEsign(s)) {
            return "completed";
        }
        return "pending";
    }

    private boolean pastEsign(ApplicationStatus s) {
        return s == ApplicationStatus.ESIGN_COMPLETED
                || s == ApplicationStatus.READY_FOR_DISBURSEMENT
                || s == ApplicationStatus.DISBURSEMENT_PENDING
                || s == ApplicationStatus.DISBURSED;
    }

    private String stateDisb(ApplicationStatus s, LoanApplication app) {
        if (s == ApplicationStatus.REJECTED || s == ApplicationStatus.WITHDRAWN) {
            return "locked";
        }
        if (s == ApplicationStatus.DISBURSED) {
            return "completed";
        }
        if (s == ApplicationStatus.READY_FOR_DISBURSEMENT || s == ApplicationStatus.DISBURSEMENT_PENDING) {
            return "in_progress";
        }
        if (pastEsign(s)) {
            return "pending";
        }
        return "pending";
    }

    private Instant ts(boolean done, LoanApplication app) {
        return done ? app.getUpdatedAt() : null;
    }
}
