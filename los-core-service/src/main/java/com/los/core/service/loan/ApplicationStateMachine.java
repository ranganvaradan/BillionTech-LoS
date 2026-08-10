package com.los.core.service.loan;

import com.los.core.model.enums.ApplicationStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.los.core.model.enums.ApplicationStatus.*;

/**
 * Declared allowed generic transitions. Orchestrated flow endpoints may enforce stricter rules.
 */
public final class ApplicationStateMachine {

    private ApplicationStateMachine() {}

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS = build();

    private static Map<ApplicationStatus, Set<ApplicationStatus>> build() {
        Map<ApplicationStatus, Set<ApplicationStatus>> m = new HashMap<>();
        m.put(DRAFT, Set.of(CONSENT_PENDING, KYC_IN_PROGRESS, WITHDRAWN));
        m.put(CONSENT_PENDING, Set.of(KYC_IN_PROGRESS, BORROWER_SUBMITTED, WITHDRAWN));
        m.put(BORROWER_SUBMITTED, Set.of(
                PENDING_CREDIT_OFFICER, KYC_IN_PROGRESS, BORROWER_SENT_BACK, WITHDRAWN));
        m.put(PENDING_CREDIT_OFFICER, Set.of(
                SENT_BACK_TO_RM, KYC_IN_PROGRESS, BORROWER_SENT_BACK, WITHDRAWN));
        m.put(SENT_BACK_TO_RM, Set.of(PENDING_CREDIT_OFFICER, BORROWER_SENT_BACK, WITHDRAWN));
        m.put(BORROWER_SENT_BACK, Set.of(BORROWER_SUBMITTED, WITHDRAWN));
        // Optional send-back (to borrower or RM) remains until sanction / eSign
        m.put(KYC_IN_PROGRESS, Set.of(
                KYC_FAILED, UNDERWRITING, ON_HOLD, WITHDRAWN, BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(KYC_FAILED, Set.of(
                KYC_IN_PROGRESS, REJECTED, WITHDRAWN, BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(UNDERWRITING, Set.of(
                REJECTED, CAM_READY, APPROVED, ON_HOLD, WITHDRAWN, UNDERWRITING_COMPLETED,
                BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(UNDERWRITING_COMPLETED, Set.of(
                CAM_READY, ON_HOLD, BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(CAM_READY, Set.of(
                CAM_REVIEWED, CAM_SENT_BACK, REJECTED, ON_HOLD, WITHDRAWN, BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(CAM_SENT_BACK, Set.of(
                CAM_READY, REJECTED, ON_HOLD, WITHDRAWN, BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(CAM_REVIEWED, Set.of(
                SANCTION_PENDING, CAM_SENT_BACK, REJECTED, ON_HOLD, SANCTIONED, WITHDRAWN,
                BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(SANCTION_PENDING, Set.of(
                SANCTIONED, ESIGN_PENDING, CAM_SENT_BACK, REJECTED, ON_HOLD, BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(SANCTIONED, Set.of(KFS_GENERATED, ON_HOLD));
        m.put(KFS_GENERATED, Set.of(ESIGN_PENDING, ON_HOLD));
        // APPROVED (legacy) — still support moves into CAM / old sanction
        m.put(APPROVED, Set.of(
                CAM_READY, CAM_REVIEWED, SANCTION_ISSUED, SANCTIONED, REJECTED, ON_HOLD, WITHDRAWN,
                BORROWER_SENT_BACK, SENT_BACK_TO_RM));
        m.put(REJECTED, Set.of());
        m.put(SANCTION_ISSUED, Set.of(ESIGN_PENDING, KFS_GENERATED, ON_HOLD));
        m.put(ESIGN_PENDING, Set.of(ESIGN_COMPLETED, SANCTIONED, ON_HOLD));
        m.put(ESIGN_COMPLETED, Set.of(READY_FOR_DISBURSEMENT, DISBURSEMENT_PENDING, DISBURSED, ON_HOLD));
        m.put(READY_FOR_DISBURSEMENT, Set.of(DISBURSED, ON_HOLD, REJECTED));
        m.put(DISBURSEMENT_PENDING, Set.of(DISBURSED, ON_HOLD));
        m.put(DISBURSED, Set.of());
        m.put(WITHDRAWN, Set.of());
        m.put(ON_HOLD, Set.of(
                KYC_IN_PROGRESS,
                UNDERWRITING,
                APPROVED,
                CAM_READY,
                CAM_SENT_BACK,
                CAM_REVIEWED,
                SANCTION_ISSUED,
                KFS_GENERATED,
                ESIGN_PENDING,
                ESIGN_COMPLETED,
                READY_FOR_DISBURSEMENT,
                DISBURSEMENT_PENDING,
                WITHDRAWN));
        return m;
    }

    public static boolean isValidTransition(ApplicationStatus from, ApplicationStatus to) {
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static Set<ApplicationStatus> getAllowedTransitions(ApplicationStatus current) {
        return VALID_TRANSITIONS.getOrDefault(current, Set.of());
    }

    public static boolean isTerminal(ApplicationStatus status) {
        return status == DISBURSED || status == REJECTED || status == WITHDRAWN;
    }
}
