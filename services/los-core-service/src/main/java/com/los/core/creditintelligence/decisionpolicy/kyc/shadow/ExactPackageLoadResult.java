package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import com.los.core.creditintelligence.decisionpolicy.sim.ExactPackageCertification;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Result of loading an exact executable package (no silent golden substitution). */
public record ExactPackageLoadResult(
        boolean ok,
        CiExecutablePolicyPackage pkg,
        boolean demoFixture,
        String code,
        String reason,
        String routingOutcome
) {
    public static ExactPackageLoadResult ok(CiExecutablePolicyPackage pkg, boolean demo) {
        return new ExactPackageLoadResult(true, pkg, demo, "OK", null,
                demo ? "DEMO_VALIDATION_FIXTURE" : "EXACTLY_ONE");
    }

    public static ExactPackageLoadResult notFound(UUID id) {
        return new ExactPackageLoadResult(false, null, false,
                ExactPackageCertification.PACKAGE_NOT_FOUND,
                "Executable package not found: " + id,
                null);
    }

    public static ExactPackageLoadResult incomplete(CiExecutablePolicyPackage pkg, String code, String reason) {
        return new ExactPackageLoadResult(false, pkg, false, code, reason, null);
    }

    public static ExactPackageLoadResult certificationFailure(CiExecutablePolicyPackage pkg, String reason) {
        return new ExactPackageLoadResult(false, pkg, false,
                ExactPackageCertification.SIMULATION_CERTIFICATION_FAILURE, reason, null);
    }

    public static ExactPackageLoadResult routingBlocked(String outcome, String reason) {
        return new ExactPackageLoadResult(false, null, false, outcome, reason, outcome);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("code", code);
        m.put("reason", reason);
        m.put("demoFixture", demoFixture);
        m.put("routingOutcome", routingOutcome);
        if (pkg != null) {
            m.put("packageId", pkg.getId());
            m.put("policyCode", pkg.getPolicyCode());
            m.put("policyVersion", pkg.getVersion());
            m.put("contentHash", pkg.getContentHash());
        }
        m.put("allowCanonicalAuthority", false);
        return m;
    }
}
