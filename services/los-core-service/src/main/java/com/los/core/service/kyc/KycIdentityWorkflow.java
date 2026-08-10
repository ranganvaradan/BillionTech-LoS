package com.los.core.service.kyc;

import com.los.core.model.enums.KycStepType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * KYC flow JSON rows that are part of the identity/verification sub-workflow
 * (executed by {@code IKycOrchestrationService#executeWorkflow}).
 * <p>
 * Excluded: bureau pull and eSign, which are separate flow steps ({@code FlowStepType} / controllers).
 */
public final class KycIdentityWorkflow {

    /**
     * Steps in {@link KycStepType} that are not run inside the KYC sub-workflow loop
     * (handled elsewhere or different product phase).
     */
    private static final Set<KycStepType> NOT_KYC_IDENTITY = EnumSet.of(
            KycStepType.VIDEO_KYC,
            KycStepType.BUREAU_PULL,
            KycStepType.ESIGN_KFS,
            KycStepType.ESIGN_AGREEMENT
    );

    /** Workflow JSON aliases that are not synchronous Run KYC identity checks. */
    private static final Set<String> NON_IDENTITY_ALIASES = Set.of("VKYC");

    private KycIdentityWorkflow() {
    }

    public static boolean isKycIdentitySubStep(KycStepType t) {
        return t != null && !NOT_KYC_IDENTITY.contains(t);
    }

    public static boolean isKycIdentitySubStepName(String stepName) {
        if (stepName == null || stepName.isBlank()) {
            return false;
        }
        String normalized = stepName.trim().toUpperCase(Locale.ROOT);
        if (NON_IDENTITY_ALIASES.contains(normalized)) {
            return false;
        }
        try {
            return isKycIdentitySubStep(KycStepType.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            // Unknown name: not bureau/eSign; let {@code executeWorkflow} attempt / fail as before.
            return true;
        }
    }
}
