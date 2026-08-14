package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.CategoryCriteria;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.OverlapWarning;
import com.los.core.customercategory.Day1ApprovedSeedCatalog.Day1CategorySpec;
import com.los.core.customercategory.Day1ApprovedSeedCatalog.Day1PolicySetSpec;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Applies the approved Day-1 15×12 matrix as DRAFT only.
 * Idempotent by stable codes; fail-closed on CONFIGURATION_CONFLICT; all-or-nothing transaction.
 * Does not touch live underwriting routing.
 */
@Service
@RequiredArgsConstructor
public class CustomerCategoryDay1SeedService {

    public static final String NOTE =
            "Day-1 approved seed DRAFT only — not ACTIVE; live underwriting unchanged";

    private final UnderwritingRuleSetRepository ruleSetRepository;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final PolicySetRepository policySetRepository;
    private final CustomerCategoryRepository categoryRepository;
    private final AdminConfigAuditSupport auditSupport;

    public record Day1PersistedRow(
            String categoryCode,
            String categoryStatus,
            String borrowerType,
            String loanProduct,
            String intakeSegment,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String policySetCode,
            String policySetStatus,
            UUID ruleSetId,
            UUID scorecardId
    ) {}

    public record Day1ApplyResult(
            int createdPolicySets,
            int createdCategories,
            int skippedExisting,
            int policySetCount,
            int categoryCount,
            int draftPolicySets,
            int draftCategories,
            int activePolicySets,
            int activeCategories,
            int unintendedOverlaps,
            boolean c100Included,
            boolean excludedProductsPresent,
            List<Day1PersistedRow> rows,
            String note
    ) {}

    @Transactional(readOnly = true)
    public void validateAllOrThrow() {
        if (Day1ApprovedSeedCatalog.POLICY_SETS.size() != 12) {
            fail("CATALOG_INVALID", "Expected 12 Policy Sets in catalog");
        }
        if (Day1ApprovedSeedCatalog.CATEGORIES.size() != 15) {
            fail("CATALOG_INVALID", "Expected 15 Categories in catalog");
        }
        Map<String, Day1PolicySetSpec> psByCode = Day1ApprovedSeedCatalog.POLICY_SETS.stream()
                .collect(Collectors.toMap(Day1PolicySetSpec::code, p -> p, (a, b) -> a, LinkedHashMap::new));

        for (Day1PolicySetSpec ps : Day1ApprovedSeedCatalog.POLICY_SETS) {
            if (ps.primaryRuleSetId().toString().equals(Day1ApprovedSeedCatalog.LEGACY_PROP_TL_RS)) {
                fail("C100_EXCLUDED", "c100…004 must not appear in Day-1 Policy Sets");
            }
            if (ps.scorecardId().toString().equalsIgnoreCase(Day1ApprovedSeedCatalog.MSME_PROP_SC)
                    || ps.scorecardId().toString().equalsIgnoreCase(Day1ApprovedSeedCatalog.SME_COMPANY_SC)) {
                fail("SPECIALISED_SCORECARD_FORBIDDEN", "MSME/SME specialised scorecards not allowed for Day-1");
            }
            UnderwritingRuleSet rs = ruleSetRepository.findById(ps.primaryRuleSetId())
                    .orElseThrow(() -> biz("RULE_SET_NOT_FOUND", "Rule set missing: " + ps.primaryRuleSetId()));
            if (!rs.isActive()) {
                fail("RULE_SET_NOT_LIVE_READY", "Rule set not ACTIVE: " + ps.primaryRuleSetId());
            }
            UnderwritingScorecard sc = scorecardRepository.findById(ps.scorecardId())
                    .orElseThrow(() -> biz("SCORECARD_NOT_FOUND", "Scorecard missing: " + ps.scorecardId()));
            if (!sc.isActive() || !"ACTIVE".equalsIgnoreCase(sc.getStatus())) {
                fail("SCORECARD_NOT_LIVE_READY", "Scorecard not ACTIVE: " + ps.scorecardId());
            }
        }

        for (Day1CategorySpec cat : Day1ApprovedSeedCatalog.CATEGORIES) {
            if (Day1ApprovedSeedCatalog.EXCLUDED_PRODUCTS.contains(cat.loanProduct())) {
                fail("EXCLUDED_PRODUCT", "Excluded product in Day-1 catalog: " + cat.loanProduct());
            }
            Day1PolicySetSpec ps = psByCode.get(cat.policySetCode());
            if (ps == null) {
                fail("POLICY_SET_SPEC_MISSING", "Category references unknown PS code: " + cat.policySetCode());
            }
            UnderwritingRuleSet rs = ruleSetRepository.findById(ps.primaryRuleSetId()).orElseThrow();
            UnderwritingScorecard sc = scorecardRepository.findById(ps.scorecardId()).orElseThrow();
            if (!cat.borrowerType().equalsIgnoreCase(rs.getBorrowerType())
                    || !cat.loanProduct().equals(rs.getLoanProduct())) {
                fail("RULE_SET_DIMENSION_MISMATCH",
                        "RS dimensions mismatch for " + cat.code());
            }
            if (!cat.borrowerType().equalsIgnoreCase(sc.getBorrowerType())
                    || !cat.loanProduct().equals(sc.getLoanProduct())) {
                fail("SCORECARD_DIMENSION_MISMATCH",
                        "Scorecard dimensions mismatch for " + cat.code());
            }
            if (!rangeFullyCovered(cat.minAmount(), cat.maxAmount(), rs.getMinAmount(), rs.getMaxAmount())) {
                fail("RULE_SET_AMOUNT_GAP", "Category amount not covered by rule set: " + cat.code());
            }
            if (!rangeFullyCovered(cat.minAmount(), cat.maxAmount(), sc.getMinAmount(), sc.getMaxAmount())) {
                fail("SCORECARD_AMOUNT_GAP", "Category amount not covered by scorecard: " + cat.code());
            }
            WorkflowConfig wf = workflowConfigRepository.findById(cat.workflowId())
                    .orElseThrow(() -> biz("WORKFLOW_NOT_FOUND", "Workflow missing: " + cat.workflowId()));
            if (!wf.isActive()) {
                fail("WORKFLOW_NOT_ACTIVE", "Workflow not active: " + cat.workflowId());
            }
            if (!cat.borrowerType().equalsIgnoreCase(wf.getBorrowerType())
                    || !cat.loanProduct().equals(wf.getLoanProduct())
                    || !cat.intakeSegment().equalsIgnoreCase(wf.getIntakeSegment())) {
                fail("WORKFLOW_DIMENSION_MISMATCH",
                        "Workflow dimensions mismatch for " + cat.code());
            }
            // conflict checks (read path)
            Optional<PolicySetEntity> existingPs = policySetRepository.findByCodeAndVersionNo(ps.code(), 1);
            if (existingPs.isPresent() && !policySetMatches(existingPs.get(), ps)) {
                fail("CONFIGURATION_CONFLICT",
                        "Policy Set " + ps.code() + " exists with different material config");
            }
            Optional<CustomerCategoryEntity> existingCc =
                    categoryRepository.findByCodeAndVersionNo(cat.code(), 1);
            if (existingCc.isPresent()) {
                CustomerCategoryEntity e = existingCc.get();
                // need PS id resolved — if PS exists use it; else conflict if category already bound differently
                UUID expectedPsId = existingPs.map(PolicySetEntity::getId).orElse(e.getPolicySetId());
                if (!categoryMatches(e, cat, expectedPsId)) {
                    fail("CONFIGURATION_CONFLICT",
                            "Category " + cat.code() + " exists with different material config");
                }
            }
        }
    }

    /**
     * Validate all references first; then persist DRAFT Policy Sets + Categories in one transaction.
     * Idempotent: existing matching codes are skipped. Conflicts fail closed (rollback).
     */
    @Transactional
    public Day1ApplyResult applyDrafts(Actor actor) {
        if (actor == null || actor.identity() == null || actor.identity().isBlank()) {
            fail("ACTOR_REQUIRED", "Authenticated actor required");
        }
        validateAllOrThrow();

        Map<String, Day1PolicySetSpec> psByCode = Day1ApprovedSeedCatalog.POLICY_SETS.stream()
                .collect(Collectors.toMap(Day1PolicySetSpec::code, p -> p, (a, b) -> a, LinkedHashMap::new));

        int createdPs = 0;
        int createdCc = 0;
        int skipped = 0;
        Map<String, UUID> psIdByCode = new LinkedHashMap<>();

        for (Day1PolicySetSpec ps : Day1ApprovedSeedCatalog.POLICY_SETS) {
            Optional<PolicySetEntity> existing = policySetRepository.findByCodeAndVersionNo(ps.code(), 1);
            if (existing.isPresent()) {
                if (!policySetMatches(existing.get(), ps)) {
                    fail("CONFIGURATION_CONFLICT",
                            "Policy Set " + ps.code() + " exists with different material config");
                }
                if (existing.get().getStatus() != ConfigLifecycleStatus.DRAFT) {
                    fail("CONFIGURATION_CONFLICT",
                            "Policy Set " + ps.code() + " exists but is not DRAFT");
                }
                psIdByCode.put(ps.code(), existing.get().getId());
                skipped++;
                continue;
            }
            PolicySetEntity e = PolicySetEntity.builder()
                    .id(UUID.randomUUID())
                    .code(ps.code())
                    .versionNo(1)
                    .name(ps.name())
                    .description("Approved Day-1 Policy Set (DRAFT)")
                    .status(ConfigLifecycleStatus.DRAFT)
                    .primaryRuleSetId(ps.primaryRuleSetId())
                    .additionalRuleSetIds(new ArrayList<>())
                    .scorecardId(ps.scorecardId())
                    .seedSourceRuleSetId(ps.primaryRuleSetId())
                    .createdAt(Instant.now())
                    .createdBy(actor.identity())
                    .updatedBy(actor.identity())
                    .build();
            policySetRepository.save(e);
            auditSupport.captureCreate("POLICY_SET", e.getId().toString(),
                    PolicySetService.snapshot(e), "Day-1 seed DRAFT Policy Set");
            psIdByCode.put(ps.code(), e.getId());
            createdPs++;
        }

        for (Day1CategorySpec cat : Day1ApprovedSeedCatalog.CATEGORIES) {
            UUID psId = psIdByCode.get(cat.policySetCode());
            if (psId == null) {
                fail("POLICY_SET_REQUIRED", "Missing Policy Set id for " + cat.policySetCode());
            }
            Optional<CustomerCategoryEntity> existing =
                    categoryRepository.findByCodeAndVersionNo(cat.code(), 1);
            if (existing.isPresent()) {
                if (!categoryMatches(existing.get(), cat, psId)) {
                    fail("CONFIGURATION_CONFLICT",
                            "Category " + cat.code() + " exists with different material config");
                }
                if (existing.get().getStatus() != ConfigLifecycleStatus.DRAFT) {
                    fail("CONFIGURATION_CONFLICT",
                            "Category " + cat.code() + " exists but is not DRAFT");
                }
                skipped++;
                continue;
            }
            Map<String, Object> notes = new LinkedHashMap<>();
            notes.put("seed", "DAY1_APPROVED_PREVIEW_2");
            notes.put("workflowId", cat.workflowId().toString());
            CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                    .id(UUID.randomUUID())
                    .code(cat.code())
                    .versionNo(1)
                    .name(cat.name())
                    .description("Approved Day-1 Customer Category (DRAFT)")
                    .status(ConfigLifecycleStatus.DRAFT)
                    .borrowerType(cat.borrowerType())
                    .loanProduct(cat.loanProduct())
                    .intakeSegment(cat.intakeSegment())
                    .minAmount(cat.minAmount())
                    .maxAmount(cat.maxAmount())
                    .policySetId(psId)
                    .seedSourceRuleSetId(psByCode.get(cat.policySetCode()).primaryRuleSetId())
                    .reviewStatus("DAY1_APPROVED")
                    .inferenceNotes(notes)
                    .createdAt(Instant.now())
                    .createdBy(actor.identity())
                    .updatedBy(actor.identity())
                    .build();
            categoryRepository.save(e);
            auditSupport.captureCreate("CUSTOMER_CATEGORY", e.getId().toString(),
                    CustomerCategoryService.snapshot(e), "Day-1 seed DRAFT Customer Category");
            createdCc++;
        }

        return readBack(createdPs, createdCc, skipped);
    }

    @Transactional(readOnly = true)
    public Day1ApplyResult readBack(int createdPs, int createdCc, int skipped) {
        List<String> day1Codes = Day1ApprovedSeedCatalog.CATEGORIES.stream()
                .map(Day1CategorySpec::code).toList();
        List<String> day1PsCodes = Day1ApprovedSeedCatalog.POLICY_SETS.stream()
                .map(Day1PolicySetSpec::code).toList();

        List<CustomerCategoryEntity> cats = categoryRepository.findAll().stream()
                .filter(c -> day1Codes.contains(c.getCode()))
                .toList();
        List<PolicySetEntity> pss = policySetRepository.findAll().stream()
                .filter(p -> day1PsCodes.contains(p.getCode()))
                .toList();

        Map<UUID, PolicySetEntity> psById = pss.stream()
                .collect(Collectors.toMap(PolicySetEntity::getId, p -> p));

        List<Day1PersistedRow> rows = new ArrayList<>();
        for (Day1CategorySpec spec : Day1ApprovedSeedCatalog.CATEGORIES) {
            CustomerCategoryEntity c = cats.stream()
                    .filter(x -> spec.code().equals(x.getCode()) && x.getVersionNo() == 1)
                    .findFirst()
                    .orElseThrow(() -> biz("SEED_READBACK_MISSING", "Missing category after apply: " + spec.code()));
            PolicySetEntity ps = psById.get(c.getPolicySetId());
            if (ps == null) {
                ps = policySetRepository.findById(c.getPolicySetId()).orElseThrow();
            }
            rows.add(new Day1PersistedRow(
                    c.getCode(),
                    c.getStatus().name(),
                    c.getBorrowerType(),
                    c.getLoanProduct(),
                    c.getIntakeSegment(),
                    c.getMinAmount(),
                    c.getMaxAmount(),
                    ps.getCode(),
                    ps.getStatus().name(),
                    ps.getPrimaryRuleSetId(),
                    ps.getScorecardId()));
        }

        List<CategoryCriteria> criteria = cats.stream()
                .filter(c -> c.getStatus() == ConfigLifecycleStatus.DRAFT
                        || c.getStatus() == ConfigLifecycleStatus.ACTIVE)
                .map(c -> new CategoryCriteria(
                        c.getCode(), c.getName(), c.getBorrowerType(), c.getLoanProduct(),
                        c.getIntakeSegment(), c.getMinAmount(), c.getMaxAmount()))
                .toList();
        List<OverlapWarning> overlaps = CustomerCategoryOverlapDetector.findOverlaps(criteria);
        // Only count same borrower+product+intake+amount (true collisions)
        int unintended = (int) overlaps.stream()
                .filter(o -> {
                    // find entities
                    CustomerCategoryEntity a = cats.stream().filter(c -> c.getCode().equals(o.leftIdOrCode())).findFirst().orElse(null);
                    CustomerCategoryEntity b = cats.stream().filter(c -> c.getCode().equals(o.rightIdOrCode())).findFirst().orElse(null);
                    if (a == null || b == null) return true;
                    return a.getBorrowerType().equals(b.getBorrowerType())
                            && a.getLoanProduct().equals(b.getLoanProduct())
                            && a.getIntakeSegment().equalsIgnoreCase(b.getIntakeSegment());
                })
                .count();

        boolean c100 = pss.stream().anyMatch(p ->
                Day1ApprovedSeedCatalog.LEGACY_PROP_TL_RS.equals(p.getPrimaryRuleSetId().toString()));
        boolean excluded = cats.stream().anyMatch(c ->
                Day1ApprovedSeedCatalog.EXCLUDED_PRODUCTS.contains(c.getLoanProduct()));

        long draftPs = pss.stream().filter(p -> p.getStatus() == ConfigLifecycleStatus.DRAFT).count();
        long activePs = pss.stream().filter(p -> p.getStatus() == ConfigLifecycleStatus.ACTIVE).count();
        long draftCc = cats.stream().filter(c -> c.getStatus() == ConfigLifecycleStatus.DRAFT).count();
        long activeCc = cats.stream().filter(c -> c.getStatus() == ConfigLifecycleStatus.ACTIVE).count();

        return new Day1ApplyResult(
                createdPs, createdCc, skipped,
                pss.size(), cats.size(),
                (int) draftPs, (int) draftCc, (int) activePs, (int) activeCc,
                unintended, c100, excluded, rows, NOTE);
    }

    static boolean rangeFullyCovered(BigDecimal catMin, BigDecimal catMax,
                                     BigDecimal authMin, BigDecimal authMax) {
        if (catMin != null && authMin != null && catMin.compareTo(authMin) < 0) {
            return false;
        }
        if (catMax != null && authMax != null && catMax.compareTo(authMax) > 0) {
            return false;
        }
        if (catMin == null && authMin != null) {
            return false;
        }
        if (catMax == null && authMax != null) {
            return false;
        }
        return true;
    }

    static boolean policySetMatches(PolicySetEntity e, Day1PolicySetSpec spec) {
        return Objects.equals(e.getPrimaryRuleSetId(), spec.primaryRuleSetId())
                && Objects.equals(e.getScorecardId(), spec.scorecardId())
                && (e.getAdditionalRuleSetIds() == null || e.getAdditionalRuleSetIds().isEmpty());
    }

    static boolean categoryMatches(CustomerCategoryEntity e, Day1CategorySpec spec, UUID expectedPsId) {
        return e.getBorrowerType().equalsIgnoreCase(spec.borrowerType())
                && e.getLoanProduct().equals(spec.loanProduct())
                && e.getIntakeSegment().equalsIgnoreCase(spec.intakeSegment())
                && amtEq(e.getMinAmount(), spec.minAmount())
                && amtEq(e.getMaxAmount(), spec.maxAmount())
                && Objects.equals(e.getPolicySetId(), expectedPsId);
    }

    private static boolean amtEq(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    private static void fail(String reason, String message) {
        throw CustomerCategoryValidator.biz(message, reason, Map.of("reason", reason));
    }

    private static RuntimeException biz(String reason, String message) {
        return CustomerCategoryValidator.biz(message, reason, Map.of("reason", reason));
    }
}
