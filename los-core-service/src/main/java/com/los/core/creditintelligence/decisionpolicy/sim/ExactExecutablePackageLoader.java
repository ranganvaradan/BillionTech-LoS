package com.los.core.creditintelligence.decisionpolicy.sim;

import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ExactPackageLoadResult;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.GoldenKycShadowPackageFactory;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.repository.CiExecutablePolicyPackageRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.ShadowPolicyRoutingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads the exact catalogue-linked executable package. Never substitutes golden content
 * for a catalogue package id.
 */
@Component
public class ExactExecutablePackageLoader {

    private final ObjectProvider<CiExecutablePolicyPackageRepository> packageRepository;

    public ExactExecutablePackageLoader(ObjectProvider<CiExecutablePolicyPackageRepository> packageRepository) {
        this.packageRepository = packageRepository;
    }

    public ExactPackageLoadResult loadById(UUID packageId, String expectedHash) {
        if (packageId == null) {
            return ExactPackageLoadResult.notFound(null);
        }
        CiExecutablePolicyPackageRepository repo = packageRepository.getIfAvailable();
        if (repo == null) {
            return ExactPackageLoadResult.notFound(packageId);
        }
        Optional<CiExecutablePolicyPackage> found = repo.findById(packageId);
        if (found.isEmpty()) {
            return ExactPackageLoadResult.notFound(packageId);
        }
        CiExecutablePolicyPackage pkg = found.get();
        if (expectedHash != null && pkg.getContentHash() != null
                && !expectedHash.equals(pkg.getContentHash())) {
            return ExactPackageLoadResult.certificationFailure(pkg,
                    "Routing content hash does not match stored package content hash");
        }
        Map<String, Object> cert = ExactPackageCertification.certifyForDecisionSimulation(
                pkg, new ExactPackageCertification.UUIDExpectation(packageId, pkg.getVersion(), expectedHash));
        if (!Boolean.TRUE.equals(cert.get("ok"))) {
            return ExactPackageLoadResult.incomplete(pkg, String.valueOf(cert.get("code")),
                    String.valueOf(cert.get("reason")));
        }
        return ExactPackageLoadResult.ok(pkg, false);
    }

    /**
     * Resolve package for shadow/simulation.
     * Catalogue EXACTLY_ONE → load exact package or fail (no golden substitute).
     * No catalogue → optional explicit demo fixture only when allowDemoFixture=true.
     */
    public ExactPackageLoadResult resolve(
            ShadowPolicyRoutingService.RoutingResult routed,
            CiExecutablePolicyPackage override,
            boolean allowDemoFixtureWhenNoCatalogue,
            UUID tenantId
    ) {
        if (override != null) {
            Map<String, Object> cert = ExactPackageCertification.certifyForDecisionSimulation(override, null);
            if (!Boolean.TRUE.equals(cert.get("ok"))
                    && !ExactPackageCertification.isExplicitDemoOrValidationFixture(override)) {
                // Demo KYC-only packages are allowed for KYC-5 matrix; for Decision sim certify separately
                if (ExactPackageCertification.PACKAGE_INCOMPLETE_FOR_DECISION_SIMULATION
                        .equals(cert.get("code"))
                        && ExactPackageCertification.isExplicitDemoOrValidationFixture(override)
                        && ExactPackageCertification.hasKycEligibilityRules(override)) {
                    return ExactPackageLoadResult.ok(override, true);
                }
                return ExactPackageLoadResult.incomplete(override,
                        String.valueOf(cert.get("code")), String.valueOf(cert.get("reason")));
            }
            return ExactPackageLoadResult.ok(override,
                    ExactPackageCertification.isExplicitDemoOrValidationFixture(override));
        }
        if (routed != null && routed.executableForShadow() && routed.executablePackageId() != null) {
            ExactPackageLoadResult loaded = loadById(routed.executablePackageId(), routed.contentHash());
            // NEVER substitute golden when catalogue linked
            return loaded;
        }
        if (routed != null) {
            String outcome = routed.outcome();
            if (PolicyApplicabilityResolver.NO_APPLICABLE_POLICY.equals(outcome)
                    || PolicyApplicabilityResolver.AMBIGUOUS_POLICY_CONFIGURATION.equals(outcome)) {
                return ExactPackageLoadResult.routingBlocked(outcome, routed.reason());
            }
        }
        if (allowDemoFixtureWhenNoCatalogue) {
            CiExecutablePolicyPackage demo = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenantId);
            return ExactPackageLoadResult.ok(demo, true);
        }
        return ExactPackageLoadResult.routingBlocked(
                routed == null ? "NO_ROUTING" : routed.outcome(),
                "No catalogue-linked executable package; golden fallback refused for non-demo path");
    }
}
