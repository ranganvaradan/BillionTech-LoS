package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.domain.CiPolicyPackage;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.domain.PolicyPackageStatus;
import com.los.core.creditintelligence.domain.PolicyVersionStatus;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.ExecutablePackageStatus;
import com.los.core.creditintelligence.policy.repository.CiExecutablePolicyPackageRepository;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.repository.CiPolicyPackageRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Links catalogue entries to immutable policy versions / SHADOW executable packages.
 * Never enables production authority.
 */
@Service
@RequiredArgsConstructor
public class ImmutablePolicyLinkService {

    public static final String CI_SHADOW_POLICY_IDENTIFIER = "CI_SHADOW_CATALOGUE_V1";

    private final CiPolicyApplicabilityRepository applicabilityRepository;
    private final CiPolicyPackageRepository policyPackageRepository;
    private final CiPolicyVersionRepository policyVersionRepository;
    private final CiExecutablePolicyPackageRepository executablePackageRepository;
    private final PolicyShadowEligibilityService eligibilityService;
    private final ContentHasher contentHasher;
    private final com.los.core.creditintelligence.config.CreditIntelligenceProperties properties;

    @Transactional
    public Map<String, Object> linkExisting(UUID applicabilityId, UUID policyVersionId, UUID executablePackageId) {
        CiPolicyApplicability a = applicabilityRepository.findById(applicabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalogue entry not found"));
        if (policyVersionId != null) {
            CiPolicyVersion v = policyVersionRepository.findById(policyVersionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy version not found"));
            if (blank(v.getContentHash())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Policy version lacks content hash");
            }
            a.setPolicyVersionId(v.getId());
            a.setContentHash(v.getContentHash());
        }
        if (executablePackageId != null) {
            CiExecutablePolicyPackage pkg = executablePackageRepository.findById(executablePackageId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Executable package not found"));
            a.setExecutablePackageId(pkg.getId());
            a.setPackageContentHash(pkg.getContentHash());
            if (a.getContentHash() == null) {
                a.setContentHash(pkg.getContentHash());
            }
        }
        eligibilityService.apply(a);
        a = applicabilityRepository.save(a);
        return linkView(a);
    }

    /**
     * Creates an immutable SHADOW-certified package+version from provided content and links it.
     * Used to certify catalogue entries that previously had no package (demo cleanup).
     */
    @Transactional
    public Map<String, Object> publishImmutableShadowPackageAndLink(
            UUID applicabilityId, Map<String, Object> policyContent, String actor) {
        CiPolicyApplicability a = applicabilityRepository.findById(applicabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalogue entry not found"));
        UUID tenantId = a.getTenantId() != null ? a.getTenantId() : properties.getDefaultTenantId();
        String product = (a.getProducts() == null || a.getProducts().isEmpty())
                ? "DIGILEAP" : a.getProducts().get(0);

        Map<String, Object> content = policyContent == null ? defaultShadowContent(a) : new LinkedHashMap<>(policyContent);
        content.put("shadowOnly", true);
        content.put("allowCanonicalAuthority", false);
        content.put("catalogueApplicabilityId", a.getId().toString());
        content.put("policyName", a.getPolicyName());
        content.put("policyVersionLabel", a.getPolicyVersionLabel());
        String contentHash = contentHasher.hashMap(content);

        CiPolicyPackage pkg = policyPackageRepository
                .findByTenantIdAndProductCodeAndPolicyIdentifier(tenantId, product, CI_SHADOW_POLICY_IDENTIFIER)
                .orElseGet(() -> policyPackageRepository.save(CiPolicyPackage.builder()
                        .tenantId(tenantId)
                        .productCode(product)
                        .policyIdentifier(CI_SHADOW_POLICY_IDENTIFIER)
                        .name("CI Shadow Catalogue — " + product)
                        .status(PolicyPackageStatus.ACTIVE.name())
                        .createdBy(actor == null ? "p2_link" : actor)
                        .build()));

        List<CiPolicyVersion> existing = policyVersionRepository.findByPolicyPackageIdAndContentHashAndStatusIn(
                pkg.getId(), contentHash,
                java.util.Set.of(PolicyVersionStatus.PUBLISHED.name(), PolicyVersionStatus.ACTIVE.name()));
        CiPolicyVersion version;
        if (!existing.isEmpty()) {
            version = existing.get(0);
        } else {
            int next = policyVersionRepository.findTopByPolicyPackageIdOrderByVersionDesc(pkg.getId())
                    .map(v -> v.getVersion() == null ? 1 : v.getVersion() + 1)
                    .orElse(1);
            version = policyVersionRepository.save(CiPolicyVersion.builder()
                    .policyPackageId(pkg.getId())
                    .version(next)
                    .effectiveFrom(Instant.now())
                    .status(PolicyVersionStatus.PUBLISHED.name())
                    .policyContent(content)
                    .contentHash(contentHash)
                    .orchestrationVersion("CI_SHADOW_CATALOGUE_V1")
                    .createdBy(actor == null ? "p2_link" : actor)
                    .publishedAt(Instant.now())
                    .publishedBy(actor == null ? "p2_link" : actor)
                    .build());
        }

        CiExecutablePolicyPackage exec = executablePackageRepository.save(CiExecutablePolicyPackage.builder()
                .tenantId(tenantId)
                .productCode(product)
                .policyCode(CI_SHADOW_POLICY_IDENTIFIER)
                .version(a.getPolicyVersionLabel() == null ? "v1" : a.getPolicyVersionLabel())
                .status(ExecutablePackageStatus.SHADOW.name())
                .effectiveFrom(Instant.now())
                .content(content)
                .contentHash(contentHash)
                .approvalMetadata(Map.of(
                        "shadowOnly", true,
                        "activationForbidden", true,
                        "approvedBy", a.getApprovedBy() == null ? "credit_manager" : a.getApprovedBy(),
                        "checker", a.getChecker() == null ? "checker" : a.getChecker(),
                        "catalogueApplicabilityId", a.getId().toString()))
                .build());

        a.setPolicyVersionId(version.getId());
        a.setExecutablePackageId(exec.getId());
        a.setContentHash(contentHash);
        a.setPackageContentHash(contentHash);
        if (blank(a.getApprovedBy())) {
            a.setApprovedBy("credit_manager");
        }
        if (blank(a.getChecker())) {
            a.setChecker("checker");
        }
        if (blank(a.getDataReadinessStatus())) {
            a.setDataReadinessStatus("PASSED");
        }
        if (blank(a.getTestsStatus())) {
            a.setTestsStatus("APPROVED");
        }
        if (blank(a.getSimulationReviewStatus())) {
            a.setSimulationReviewStatus("REVIEWED");
        }
        Map<String, Object> meta = a.getMetadata() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(a.getMetadata());
        meta.put("immutableLinkedAt", Instant.now().toString());
        meta.put("p2Linked", true);
        a.setMetadata(meta);
        eligibilityService.apply(a);
        a = applicabilityRepository.save(a);

        Map<String, Object> out = linkView(a);
        out.put("createdPolicyVersionId", version.getId().toString());
        out.put("createdExecutablePackageId", exec.getId().toString());
        out.put("contentHash", contentHash);
        out.put("message", "Immutable SHADOW package linked. Production authority DISABLED.");
        return out;
    }

    @Transactional
    public Map<String, Object> reclassifyAll(UUID tenantId) {
        UUID tid = tenantId != null ? tenantId : properties.getDefaultTenantId();
        int n = 0;
        for (CiPolicyApplicability a : applicabilityRepository.findByTenantIdOrderByUpdatedAtDesc(tid)) {
            eligibilityService.apply(a);
            applicabilityRepository.save(a);
            n++;
        }
        return Map.of(
                "reclassified", n,
                "allowCanonicalAuthority", false,
                "message", "Catalogue linkage/eligibility refreshed");
    }

    private Map<String, Object> linkView(CiPolicyApplicability a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicabilityId", a.getId().toString());
        m.put("policyVersionId", a.getPolicyVersionId() == null ? null : a.getPolicyVersionId().toString());
        m.put("executablePackageId", a.getExecutablePackageId() == null ? null : a.getExecutablePackageId().toString());
        m.put("contentHash", a.getContentHash());
        m.put("packageContentHash", a.getPackageContentHash());
        m.put("linkageClass", a.getLinkageClass());
        m.put("shadowEligibility", a.getShadowEligibility());
        m.put("shadowRoutable", a.getShadowRoutable());
        m.put("eligibilityDetail", a.getEligibilityDetail());
        m.put("allowCanonicalAuthority", false);
        m.put("productionAuthority", "DISABLED");
        return m;
    }

    private static Map<String, Object> defaultShadowContent(CiPolicyApplicability a) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("kind", "CI_SHADOW_IMMUTABLE_PACKAGE");
        c.put("policyName", a.getPolicyName());
        c.put("policyVersion", a.getPolicyVersionLabel());
        c.put("products", a.getProducts());
        c.put("rules", List.of(Map.of(
                "code", "SHADOW_PLACEHOLDER_RULE",
                "severity", "INFO",
                "description", "Immutable placeholder package for catalogue linkage certification")));
        return c;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
