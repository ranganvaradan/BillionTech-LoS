package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.ExecutablePackageStatus;
import com.los.core.creditintelligence.policy.repository.CiExecutablePolicyPackageRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Certifies whether a catalogue entry may shadow-route real applications.
 * Business ACTIVE ≠ executable certified for shadow.
 */
@Service
@RequiredArgsConstructor
public class PolicyShadowEligibilityService {

    private final CiPolicyVersionRepository policyVersionRepository;
    private final CiExecutablePolicyPackageRepository executablePackageRepository;

    public record EligibilityResult(
            boolean eligible,
            String linkageClass,
            String shadowEligibility,
            String contentHash,
            String packageContentHash,
            UUID policyVersionId,
            UUID executablePackageId,
            Map<String, Object> detail
    ) {}

    public EligibilityResult evaluate(CiPolicyApplicability a) {
        Map<String, Object> detail = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        if (a == null) {
            failures.add("Catalogue entry missing");
            return ineligible(PolicyShadowRoutingOutcomes.LINK_INVALID,
                    PolicyShadowRoutingOutcomes.NOT_ELIGIBLE, null, null, null, null, failures, detail);
        }

        String status = a.getBusinessStatus() == null ? "" : a.getBusinessStatus().toUpperCase(Locale.ROOT);
        boolean lifecycleOk = List.of("ACTIVE", "SCHEDULED", "APPROVED").contains(status);
        detail.put("businessStatus", a.getBusinessStatus());
        detail.put("lifecycleOk", lifecycleOk);
        if (!lifecycleOk) {
            failures.add("Lifecycle status not ACTIVE/SCHEDULED/APPROVED");
        }

        boolean cm = blank(a.getApprovedBy()) == false;
        boolean checker = blank(a.getChecker()) == false;
        detail.put("creditManagerApproved", cm);
        detail.put("checkerApproved", checker);
        if (!cm) {
            failures.add("Credit Manager approval missing");
        }
        if (!checker) {
            failures.add("Checker approval missing");
        }

        boolean readiness = passed(a.getDataReadinessStatus());
        boolean tests = passed(a.getTestsStatus());
        boolean sim = passed(a.getSimulationReviewStatus());
        detail.put("dataReadinessPassed", readiness);
        detail.put("testsApproved", tests);
        detail.put("simulationReviewed", sim);
        if (!readiness) {
            failures.add("Data Readiness not passed");
        }
        if (!tests) {
            failures.add("Tests not approved");
        }
        if (!sim) {
            failures.add("Simulation not reviewed");
        }

        UUID versionId = a.getPolicyVersionId();
        UUID execId = a.getExecutablePackageId();
        String contentHash = null;
        String packageHash = null;
        boolean versionOk = false;
        boolean execOk = false;

        if (versionId != null) {
            CiPolicyVersion v = policyVersionRepository.findById(versionId).orElse(null);
            if (v == null) {
                failures.add("policyVersionId not found");
            } else if (blank(v.getContentHash())) {
                failures.add("Policy version content hash missing");
            } else {
                versionOk = true;
                contentHash = v.getContentHash();
                detail.put("policyVersionStatus", v.getStatus());
                detail.put("policyVersionContentHash", contentHash);
            }
        }
        if (execId != null) {
            CiExecutablePolicyPackage pkg = executablePackageRepository.findById(execId).orElse(null);
            if (pkg == null) {
                failures.add("executablePackageId not found");
            } else {
                String st = pkg.getStatus() == null ? "" : pkg.getStatus().toUpperCase(Locale.ROOT);
                boolean shadowStatus = ExecutablePackageStatus.SHADOW.name().equals(st)
                        || "APPROVED".equals(st)
                        || "APPROVED_FOR_SHADOW".equals(st);
                detail.put("executablePackageStatus", pkg.getStatus());
                if (!shadowStatus) {
                    failures.add("Executable package status not SHADOW/approved-for-shadow: " + pkg.getStatus());
                } else if (blank(pkg.getContentHash())) {
                    failures.add("Executable package content hash missing");
                } else {
                    execOk = true;
                    packageHash = pkg.getContentHash();
                    if (contentHash == null) {
                        contentHash = packageHash;
                    }
                }
            }
        }

        boolean linked = versionOk || execOk;
        detail.put("immutablePackageLinked", linked);
        detail.put("policyVersionLinked", versionOk);
        detail.put("executablePackageLinked", execOk);
        if (!linked) {
            failures.add("Require policyVersionId and/or executablePackageId with immutable content hash");
        }

        detail.put("failures", failures);
        detail.put("productionAuthority", "DISABLED");
        detail.put("allowCanonicalAuthority", false);

        if (!failures.isEmpty()) {
            String linkage = linked ? PolicyShadowRoutingOutcomes.LINK_INVALID
                    : (isDemoSeed(a) ? PolicyShadowRoutingOutcomes.LINK_DEMO_UNLINKED
                    : PolicyShadowRoutingOutcomes.LINK_UNLINKED);
            String elig = !linked ? PolicyShadowRoutingOutcomes.POLICY_PACKAGE_NOT_EXECUTABLE
                    : PolicyShadowRoutingOutcomes.NOT_ELIGIBLE;
            if (isDemoSeed(a) && !linked) {
                elig = PolicyShadowRoutingOutcomes.DEMO_ONLY_NOT_ROUTABLE;
                linkage = PolicyShadowRoutingOutcomes.LINK_DEMO_NOT_ROUTABLE;
            }
            return ineligible(linkage, elig, contentHash, packageHash, versionId, execId, failures, detail);
        }

        detail.put("message", "Eligible for shadow routing — production authority DISABLED");
        return new EligibilityResult(
                true,
                PolicyShadowRoutingOutcomes.LINK_PROPER,
                PolicyShadowRoutingOutcomes.ELIGIBLE,
                contentHash,
                packageHash,
                versionId,
                execId,
                detail);
    }

    public void apply(CiPolicyApplicability a) {
        EligibilityResult r = evaluate(a);
        a.setLinkageClass(r.linkageClass());
        a.setShadowEligibility(r.shadowEligibility());
        a.setShadowRoutable(r.eligible());
        a.setContentHash(r.contentHash());
        a.setPackageContentHash(r.packageContentHash());
        a.setEligibilityDetail(r.detail());
        a.setProductionAuthorityEnabled(false);
    }

    private static EligibilityResult ineligible(
            String linkage, String elig, String hash, String pkgHash,
            UUID versionId, UUID execId, List<String> failures, Map<String, Object> detail) {
        detail.put("eligible", false);
        detail.put("failures", failures);
        return new EligibilityResult(false, linkage, elig, hash, pkgHash, versionId, execId, detail);
    }

    private static boolean isDemoSeed(CiPolicyApplicability a) {
        if (a.getCreatedBy() != null && a.getCreatedBy().toLowerCase(Locale.ROOT).contains("smoke")) {
            return true;
        }
        if (a.getReasonForChange() != null && a.getReasonForChange().toLowerCase(Locale.ROOT).contains("p1 smoke")) {
            return true;
        }
        Map<String, Object> meta = a.getMetadata();
        return meta != null && Boolean.TRUE.equals(meta.get("p1Smoke"));
    }

    private static boolean passed(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        return u.equals("PASSED") || u.equals("APPROVED") || u.equals("REVIEWED")
                || u.equals("COMPLETE") || u.equals("OK") || u.equals("PASS");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
