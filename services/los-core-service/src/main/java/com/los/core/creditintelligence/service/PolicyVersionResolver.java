package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiPolicyPackage;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.domain.PolicyPackageStatus;
import com.los.core.creditintelligence.domain.PolicyVersionStatus;
import com.los.core.creditintelligence.repository.CiPolicyPackageRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Freezes active hard-rule and scorecard configuration into an immutable policy version.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyVersionResolver {

    public static final String POLICY_IDENTIFIER = "UNDERWRITE_LEGACY_V1";
    public static final String ORCHESTRATION_VERSION = "LEGACY_UNDERWRITE_APPLICATION_V1";
    public static final String MERGE_PRECEDENCE = "HARD_RULE > SCORECARD > RULES > LEGACY";

    private final UnderwritingRuleSetRepository ruleSetRepository;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final CiPolicyPackageRepository policyPackageRepository;
    private final CiPolicyVersionRepository policyVersionRepository;
    private final ContentHasher contentHasher;
    private final CreditIntelligenceProperties properties;
    private final AuditService auditService;

    @Transactional
    public CiPolicyVersion resolveAndFreeze(LoanApplication app, String createdBy) {
        UUID tenantId = properties.getDefaultTenantId();
        String productCode = app.getLoanProduct() != null ? app.getLoanProduct() : "UNKNOWN";
        String borrowerType = app.getBorrowerType() != null ? app.getBorrowerType().name() : "UNKNOWN";

        List<UnderwritingRuleSet> ruleSets = ruleSetRepository
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(borrowerType, productCode);
        List<UnderwritingScorecard> scorecards = scorecardRepository
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(borrowerType, productCode);

        Map<String, Object> policyContent = buildPolicyContent(productCode, borrowerType, ruleSets, scorecards);
        String contentHash = contentHasher.hashMap(policyContent);

        CiPolicyPackage pkg = policyPackageRepository
                .findByTenantIdAndProductCodeAndPolicyIdentifier(tenantId, productCode, POLICY_IDENTIFIER)
                .orElseGet(() -> policyPackageRepository.save(CiPolicyPackage.builder()
                        .tenantId(tenantId)
                        .productCode(productCode)
                        .policyIdentifier(POLICY_IDENTIFIER)
                        .name("Legacy underwriting — " + productCode)
                        .status(PolicyPackageStatus.ACTIVE.name())
                        .createdBy(createdBy)
                        .build()));

        List<CiPolicyVersion> existing = policyVersionRepository.findByPolicyPackageIdAndContentHashAndStatusIn(
                pkg.getId(),
                contentHash,
                Set.of(PolicyVersionStatus.PUBLISHED.name(), PolicyVersionStatus.ACTIVE.name()));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        int nextVersion = policyVersionRepository.findTopByPolicyPackageIdOrderByVersionDesc(pkg.getId())
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        List<Map<String, Object>> refs = new ArrayList<>();
        for (UnderwritingRuleSet r : ruleSets) {
            refs.add(Map.of(
                    "type", "RULE_SET",
                    "id", r.getId().toString(),
                    "name", r.getName() != null ? r.getName() : "",
                    "priority", r.getPriority()));
        }
        for (UnderwritingScorecard s : scorecards) {
            refs.add(Map.of(
                    "type", "SCORECARD",
                    "id", s.getId().toString(),
                    "name", s.getName() != null ? s.getName() : "",
                    "version", s.getVersion(),
                    "priority", s.getPriority()));
        }

        Instant now = Instant.now();
        CiPolicyVersion version = CiPolicyVersion.builder()
                .policyPackageId(pkg.getId())
                .version(nextVersion)
                .effectiveFrom(now)
                .status(PolicyVersionStatus.PUBLISHED.name())
                .policyContent(policyContent)
                .contentHash(contentHash)
                .sourcePolicyReferences(refs)
                .orchestrationVersion(ORCHESTRATION_VERSION)
                .createdBy(createdBy)
                .publishedAt(now)
                .publishedBy(createdBy)
                .build();
        version = policyVersionRepository.save(version);

        auditService.logEvent(
                app.getId(),
                "CREDIT_INTELLIGENCE",
                "policy.version.published",
                null,
                null,
                Map.of(
                        "policyVersionId", version.getId().toString(),
                        "policyPackageId", pkg.getId().toString(),
                        "contentHash", contentHash,
                        "version", nextVersion),
                "Policy version published for shadow evaluation");
        return version;
    }

    private static Map<String, Object> buildPolicyContent(
            String productCode,
            String borrowerType,
            List<UnderwritingRuleSet> ruleSets,
            List<UnderwritingScorecard> scorecards) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("orchestrationVersion", ORCHESTRATION_VERSION);
        content.put("mergePrecedence", MERGE_PRECEDENCE);
        content.put("productCode", productCode);
        content.put("borrowerType", borrowerType);

        List<Map<String, Object>> ruleSetMaps = new ArrayList<>();
        for (UnderwritingRuleSet r : ruleSets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId() != null ? r.getId().toString() : null);
            m.put("name", r.getName());
            m.put("borrowerType", r.getBorrowerType());
            m.put("loanProduct", r.getLoanProduct());
            m.put("minAmount", r.getMinAmount() != null ? r.getMinAmount().toPlainString() : null);
            m.put("maxAmount", r.getMaxAmount() != null ? r.getMaxAmount().toPlainString() : null);
            m.put("geography", r.getGeography());
            m.put("minTenureMonths", r.getMinTenureMonths());
            m.put("maxTenureMonths", r.getMaxTenureMonths());
            m.put("priority", r.getPriority());
            m.put("active", r.isActive());
            m.put("rulesJson", r.getRulesJson());
            ruleSetMaps.add(m);
        }
        content.put("ruleSets", ruleSetMaps);

        List<Map<String, Object>> scorecardMaps = new ArrayList<>();
        for (UnderwritingScorecard s : scorecards) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId() != null ? s.getId().toString() : null);
            m.put("name", s.getName());
            m.put("borrowerType", s.getBorrowerType());
            m.put("loanProduct", s.getLoanProduct());
            m.put("version", s.getVersion());
            m.put("priority", s.getPriority());
            m.put("minAmount", s.getMinAmount() != null ? s.getMinAmount().toPlainString() : null);
            m.put("maxAmount", s.getMaxAmount() != null ? s.getMaxAmount().toPlainString() : null);
            m.put("geography", s.getGeography());
            m.put("scorecardJson", s.getScorecardJson());
            m.put("thresholdsJson", s.getThresholdsJson());
            m.put("hardRulesJson", s.getHardRulesJson());
            m.put("active", s.isActive());
            scorecardMaps.add(m);
        }
        content.put("scorecards", scorecardMaps);
        return content;
    }
}
