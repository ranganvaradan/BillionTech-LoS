package com.los.core.creditintelligence.decisionpolicy.kyc;

import com.los.core.model.enums.KycStepType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Design-time KYC capability evidence derived from repository provider implementations.
 * Does NOT read AggregatorConfig / credentials — that is optional overlay via
 * {@link KycIntegrationRoutingProbe}.
 *
 * <p>Enum presence alone is not enough: EMAIL_OTP exists as a step but has no supporting
 * provider implementation → not READY.
 */
public final class KycCapabilityEvidence {

    private KycCapabilityEvidence() {}

    /**
     * Steps with at least one real {@code IKycProvider} implementation in this repository
     * (Karza, Authbridge, Hyperverge, CkycRegistry, Perfios).
     */
    public static final Set<KycStepType> STEPS_WITH_PROVIDER_IMPLEMENTATION = Set.of(
            KycStepType.PAN_VERIFY,
            KycStepType.GSTIN_VERIFY,
            KycStepType.AADHAAR_OTP,
            KycStepType.DL_VERIFY,
            KycStepType.VOTER_ID_VERIFY,
            KycStepType.BANK_PENNY_DROP,
            KycStepType.UDYAM_VERIFY,
            KycStepType.CIN_MCA21,
            KycStepType.MNRL,
            KycStepType.MOBILE_OTP,
            KycStepType.CKYC_DOWNLOAD,
            KycStepType.AML_SCREENING,
            KycStepType.FACE_MATCH,
            KycStepType.LIVENESS,
            KycStepType.VIDEO_KYC
    );

    /** Known primary / fallback provider labels (business names — not bean class names). */
    private static final Map<KycStepType, List<String>> PROVIDER_LABELS = Map.ofEntries(
            Map.entry(KycStepType.PAN_VERIFY, List.of("Karza", "Authbridge")),
            Map.entry(KycStepType.GSTIN_VERIFY, List.of("Karza", "Authbridge")),
            Map.entry(KycStepType.AADHAAR_OTP, List.of("Karza", "Authbridge")),
            Map.entry(KycStepType.BANK_PENNY_DROP, List.of("Karza", "Authbridge")),
            Map.entry(KycStepType.UDYAM_VERIFY, List.of("Karza", "Authbridge")),
            Map.entry(KycStepType.CIN_MCA21, List.of("Karza", "Authbridge")),
            Map.entry(KycStepType.CKYC_DOWNLOAD, List.of("Authbridge / CKYC Registry")),
            Map.entry(KycStepType.VIDEO_KYC, List.of("Hyperverge")),
            Map.entry(KycStepType.MOBILE_OTP, List.of("Authbridge")),
            Map.entry(KycStepType.FACE_MATCH, List.of("Hyperverge")),
            Map.entry(KycStepType.LIVENESS, List.of("Hyperverge"))
    );

    public static Optional<KycStepType> stepForFact(String factPath) {
        if (factPath == null || factPath.isBlank()) {
            return Optional.empty();
        }
        String p = factPath.toLowerCase(Locale.ROOT);
        if (p.startsWith("kyc.pan.")) {
            return Optional.of(KycStepType.PAN_VERIFY);
        }
        if (p.startsWith("kyc.ckyc.")) {
            return Optional.of(KycStepType.CKYC_DOWNLOAD);
        }
        if (p.startsWith("kyc.aadhaar.")) {
            return Optional.of(KycStepType.AADHAAR_OTP);
        }
        if (p.startsWith("kyc.gstin.")) {
            return Optional.of(KycStepType.GSTIN_VERIFY);
        }
        if (p.startsWith("kyc.cin.")) {
            return Optional.of(KycStepType.CIN_MCA21);
        }
        if (p.startsWith("kyc.udyam.")) {
            return Optional.of(KycStepType.UDYAM_VERIFY);
        }
        if (p.startsWith("kyc.bank_account.")) {
            return Optional.of(KycStepType.BANK_PENNY_DROP);
        }
        if (p.startsWith("kyc.vkyc.") || p.startsWith("kyc.pkyc.")) {
            return Optional.of(KycStepType.VIDEO_KYC);
        }
        if (p.startsWith("kyc.email.")) {
            return Optional.of(KycStepType.EMAIL_OTP);
        }
        return Optional.empty();
    }

    public static boolean hasProviderImplementation(KycStepType step) {
        return step != null && STEPS_WITH_PROVIDER_IMPLEMENTATION.contains(step);
    }

    public static boolean hasWorkflowCapability(KycStepType step) {
        // Workflow engine can express any KycStepType; design capability = step is a known
        // identity/verification step with an implementation path.
        return hasProviderImplementation(step);
    }

    public static String primaryProviderLabel(KycStepType step) {
        List<String> labels = PROVIDER_LABELS.get(step);
        return labels == null || labels.isEmpty() ? null : labels.get(0);
    }

    public static String fallbackProviderLabel(KycStepType step) {
        List<String> labels = PROVIDER_LABELS.get(step);
        return labels == null || labels.size() < 2 ? null : labels.get(1);
    }

    public static Map<String, Object> describeStep(KycStepType step) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("workflowStep", step == null ? null : step.name());
        m.put("workflowCapability", hasWorkflowCapability(step));
        m.put("providerImplementationPresent", hasProviderImplementation(step));
        m.put("primaryProvider", primaryProviderLabel(step));
        m.put("fallbackProvider", fallbackProviderLabel(step));
        m.put("enumOnlyWithoutImplementation",
                step != null && !hasProviderImplementation(step));
        return m;
    }

    private static final Set<String> APPLICATION_INPUTS = Set.of(
            "borrower_type", "borrowertype", "requested_amount", "loan_amount",
            "applicant_name", "customer_name", "pan", "gstin", "cin",
            "proposed_edi", "product", "region", "customer_segment", "name"
    );

    /** Application / intake fields proven in LOS model vocabulary. */
    public static boolean applicationInputExists(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT).trim();
        if (p.startsWith("application.")) {
            p = p.substring("application.".length());
        }
        return APPLICATION_INPUTS.contains(p);
    }
}
