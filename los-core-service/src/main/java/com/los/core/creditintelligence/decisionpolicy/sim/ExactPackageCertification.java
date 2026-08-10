package com.los.core.creditintelligence.decisionpolicy.sim;

import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exact package certification helpers for KYC-7.
 * Catalogue-linked packages must supply their own KYC/credit content — no golden substitution.
 */
public final class ExactPackageCertification {

    public static final String PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION =
            "PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION";
    public static final String SIMULATION_CERTIFICATION_FAILURE =
            "SIMULATION_CERTIFICATION_FAILURE";
    public static final String PACKAGE_NOT_FOUND = "PACKAGE_NOT_FOUND";

    private ExactPackageCertification() {}

    /** True when the package itself is an explicit DEMO / VALIDATION FIXTURE. */
    public static boolean isExplicitDemoOrValidationFixture(CiExecutablePolicyPackage pkg) {
        if (pkg == null) {
            return false;
        }
        if (pkg.getApprovalMetadata() != null) {
            Object demo = pkg.getApprovalMetadata().get("demoLabel");
            if (demo != null && String.valueOf(demo).toUpperCase(Locale.ROOT).contains("VALIDATION FIXTURE")) {
                return true;
            }
            if (Boolean.TRUE.equals(pkg.getApprovalMetadata().get("fixtureOnly"))) {
                return true;
            }
        }
        if (pkg.getContent() != null && pkg.getContent().get("metadata") instanceof Map<?, ?> meta) {
            Object demo = meta.get("demoLabel");
            if (demo != null && String.valueOf(demo).toUpperCase(Locale.ROOT).contains("VALIDATION FIXTURE")) {
                return true;
            }
            if (Boolean.TRUE.equals(meta.get("fixtureOnly"))) {
                return true;
            }
        }
        String code = pkg.getPolicyCode() == null ? "" : pkg.getPolicyCode().toUpperCase(Locale.ROOT);
        return code.contains("VALIDATION") || code.contains("_DEMO_") || code.startsWith("DEMO_");
    }

    public static boolean hasKycEligibilityRules(CiExecutablePolicyPackage pkg) {
        return !ShadowKycPolicyEvaluationService.selectKycEligibilityRules(pkg).isEmpty();
    }

    public static boolean hasCreditOrDecisionRules(CiExecutablePolicyPackage pkg) {
        if (pkg == null || pkg.getContent() == null) {
            return false;
        }
        Object rules = pkg.getContent().get("rules");
        if (!(rules instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) raw;
            if (!ShadowKycPolicyEvaluationService.isKycEligibilityRule(rule)) {
                return true;
            }
        }
        // Decision strategy embedded in package also counts
        return pkg.getContent().get("decisionStrategy") instanceof Map<?, ?>
                || pkg.getContent().get("scorecard") instanceof Map<?, ?>;
    }

    /**
     * Catalogue-linked packages that lack KYC rules are incomplete for Decision Policy simulation.
     * Demo fixtures may still be KYC-only for KYC-5 matrix.
     */
    public static Map<String, Object> certifyForDecisionSimulation(
            CiExecutablePolicyPackage pkg,
            UUIDExpectation expected
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("shadow", true);
        if (pkg == null) {
            out.put("ok", false);
            out.put("code", PACKAGE_NOT_FOUND);
            out.put("reason", "Executable package not found");
            return out;
        }
        if (expected != null) {
            if (expected.packageId() != null && pkg.getId() != null
                    && !expected.packageId().equals(pkg.getId())) {
                out.put("ok", false);
                out.put("code", SIMULATION_CERTIFICATION_FAILURE);
                out.put("reason", "Resolved package id does not match EvaluationContext / routing package id");
                return out;
            }
            if (expected.contentHash() != null && pkg.getContentHash() != null
                    && !expected.contentHash().equals(pkg.getContentHash())) {
                out.put("ok", false);
                out.put("code", SIMULATION_CERTIFICATION_FAILURE);
                out.put("reason", "Package content hash mismatch");
                return out;
            }
            if (expected.version() != null && pkg.getVersion() != null
                    && !expected.version().equals(pkg.getVersion())) {
                out.put("ok", false);
                out.put("code", SIMULATION_CERTIFICATION_FAILURE);
                out.put("reason", "Package version mismatch");
                return out;
            }
        }
        boolean demo = isExplicitDemoOrValidationFixture(pkg);
        if (!hasKycEligibilityRules(pkg)) {
            out.put("ok", false);
            out.put("code", PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION);
            out.put("reason", "Executable package has no KYC/ELIGIBILITY rules — refusing golden fallback");
            out.put("demoPackage", demo);
            return out;
        }
        out.put("ok", true);
        out.put("demoPackage", demo);
        out.put("policyCode", pkg.getPolicyCode());
        out.put("policyVersion", pkg.getVersion());
        out.put("packageId", pkg.getId());
        out.put("contentHash", pkg.getContentHash());
        return out;
    }

    public record UUIDExpectation(java.util.UUID packageId, String version, String contentHash) {}
}
