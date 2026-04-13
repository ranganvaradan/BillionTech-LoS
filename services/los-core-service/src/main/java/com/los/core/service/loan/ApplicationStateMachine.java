package com.los.core.service.loan;

import com.los.core.model.enums.ApplicationStatus;

import java.util.Map;
import java.util.Set;

import static com.los.core.model.enums.ApplicationStatus.*;

public final class ApplicationStateMachine {

    private ApplicationStateMachine() {}

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(DRAFT,                Set.of(CONSENT_PENDING, WITHDRAWN)),
            Map.entry(CONSENT_PENDING,      Set.of(KYC_IN_PROGRESS, WITHDRAWN)),
            Map.entry(KYC_IN_PROGRESS,      Set.of(KYC_FAILED, UNDERWRITING, ON_HOLD, WITHDRAWN)),
            Map.entry(KYC_FAILED,           Set.of(KYC_IN_PROGRESS, REJECTED, WITHDRAWN)),
            Map.entry(UNDERWRITING,         Set.of(APPROVED, REJECTED, ON_HOLD)),
            Map.entry(APPROVED,             Set.of(SANCTION_ISSUED, REJECTED, ON_HOLD)),
            Map.entry(REJECTED,             Set.of()),
            Map.entry(SANCTION_ISSUED,      Set.of(ESIGN_PENDING, ON_HOLD)),
            Map.entry(ESIGN_PENDING,        Set.of(DISBURSEMENT_PENDING, ON_HOLD)),
            Map.entry(DISBURSEMENT_PENDING, Set.of(DISBURSED, ON_HOLD)),
            Map.entry(DISBURSED,            Set.of()),
            Map.entry(WITHDRAWN,            Set.of()),
            Map.entry(ON_HOLD,              Set.of(KYC_IN_PROGRESS, UNDERWRITING, APPROVED, SANCTION_ISSUED, ESIGN_PENDING, DISBURSEMENT_PENDING, WITHDRAWN))
    );

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
