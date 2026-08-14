package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.SeedApplyResponse;
import com.los.core.customercategory.CustomerCategoryDtos.SeedCandidateView;
import com.los.core.customercategory.CustomerCategoryDtos.SeedPreviewResponse;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.CategoryCriteria;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.OverlapWarning;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic seed from {@code underwriting_rule_sets}.
 * Intake segment is not on rule sets → {@code ANY} with REVIEW_REQUIRED note
 * (live UW today matches without intake filter = implicit ANY).
 * Never creates ACTIVE records. Not invoked on startup.
 */
@Service
@RequiredArgsConstructor
public class CustomerCategorySeedService {

    public static final String REVIEW_REQUIRED = "REVIEW_REQUIRED";

    private final UnderwritingRuleSetRepository ruleSetRepository;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final PolicySetRepository policySetRepository;
    private final CustomerCategoryRepository categoryRepository;
    private final AdminConfigAuditSupport auditSupport;

    @Transactional(readOnly = true)
    public SeedPreviewResponse preview() {
        List<SeedCandidateView> candidates = buildCandidates(false);
        List<CategoryCriteria> criteria = candidates.stream()
                .map(c -> new CategoryCriteria(
                        c.categoryCode(),
                        c.categoryName(),
                        c.borrowerType(),
                        c.loanProduct(),
                        c.intakeSegment(),
                        parseMin(c.amountRange()),
                        parseMax(c.amountRange())))
                .toList();
        List<OverlapWarning> overlaps = CustomerCategoryOverlapDetector.findOverlaps(criteria);
        List<Map<String, Object>> pairwise = overlaps.stream().map(this::overlapMap).toList();
        int already = (int) candidates.stream().filter(SeedCandidateView::alreadySeeded).count();
        return new SeedPreviewResponse(candidates.size(), already, candidates, pairwise);
    }

    /**
     * Persist DRAFT Policy Sets + Categories for candidates not already seeded.
     * Idempotent by stable codes {@code PS_SEED_<ruleSetId>} / {@code CC_SEED_<ruleSetId>}.
     */
    @Transactional
    public SeedApplyResponse applyDrafts(Actor actor) {
        if (actor == null || actor.identity() == null || actor.identity().isBlank()) {
            throw CustomerCategoryValidator.biz("Authenticated actor required", "ACTOR_REQUIRED", Map.of());
        }
        List<SeedCandidateView> preview = buildCandidates(false);
        int createdPs = 0;
        int createdCc = 0;
        int skipped = 0;
        List<SeedCandidateView> created = new ArrayList<>();

        for (SeedCandidateView c : preview) {
            if (c.alreadySeeded()) {
                skipped++;
                continue;
            }
            UnderwritingRuleSet rs = ruleSetRepository.findById(c.sourceRuleSetId()).orElse(null);
            if (rs == null || !rs.isActive()) {
                skipped++;
                continue;
            }
            String psCode = policySetCode(rs.getId());
            String ccCode = categoryCode(rs.getId());
            Optional<PolicySetEntity> existingPs = policySetRepository.findByCodeAndVersionNo(psCode, 1);
            PolicySetEntity ps = existingPs.orElseGet(() -> {
                PolicySetEntity e = PolicySetEntity.builder()
                        .id(UUID.randomUUID())
                        .code(psCode)
                        .versionNo(1)
                        .name(c.policySetName())
                        .description("Seeded from underwriting_rule_sets id=" + rs.getId())
                        .status(ConfigLifecycleStatus.DRAFT)
                        .primaryRuleSetId(rs.getId())
                        .additionalRuleSetIds(new ArrayList<>())
                        .scorecardId(c.scorecardId())
                        .seedSourceRuleSetId(rs.getId())
                        .createdAt(Instant.now())
                        .createdBy(actor.identity())
                        .updatedBy(actor.identity())
                        .build();
                return policySetRepository.save(e);
            });
            if (existingPs.isEmpty()) {
                createdPs++;
                auditSupport.captureCreate("POLICY_SET", ps.getId().toString(),
                        PolicySetService.snapshot(ps), "Seed DRAFT Policy Set");
            }

            Optional<CustomerCategoryEntity> existingCc =
                    categoryRepository.findByCodeAndVersionNo(ccCode, 1);
            if (existingCc.isPresent()) {
                skipped++;
                continue;
            }
            Map<String, Object> notes = new LinkedHashMap<>(c.inferenceSource());
            CustomerCategoryEntity cat = CustomerCategoryEntity.builder()
                    .id(UUID.randomUUID())
                    .code(ccCode)
                    .versionNo(1)
                    .name(c.categoryName())
                    .description("Seeded DRAFT from rule set " + rs.getName())
                    .status(ConfigLifecycleStatus.DRAFT)
                    .borrowerType(c.borrowerType())
                    .loanProduct(c.loanProduct())
                    .intakeSegment(c.intakeSegment())
                    .minAmount(rs.getMinAmount())
                    .maxAmount(rs.getMaxAmount())
                    .policySetId(ps.getId())
                    .seedSourceRuleSetId(rs.getId())
                    .reviewStatus(REVIEW_REQUIRED)
                    .inferenceNotes(notes)
                    .createdAt(Instant.now())
                    .createdBy(actor.identity())
                    .updatedBy(actor.identity())
                    .build();
            categoryRepository.save(cat);
            createdCc++;
            auditSupport.captureCreate("CUSTOMER_CATEGORY", cat.getId().toString(),
                    CustomerCategoryService.snapshot(cat), "Seed DRAFT Customer Category");
            created.add(c);
        }

        return new SeedApplyResponse(createdCc, createdPs, skipped, created,
                "DRAFT only — not ACTIVE; live underwriting unchanged");
    }

    private List<SeedCandidateView> buildCandidates(boolean unused) {
        List<UnderwritingRuleSet> active = ruleSetRepository.findAll().stream()
                .filter(UnderwritingRuleSet::isActive)
                .sorted(Comparator.comparing((UnderwritingRuleSet r) -> r.getBorrowerType() == null ? "" : r.getBorrowerType())
                        .thenComparing(r -> r.getLoanProduct() == null ? "" : r.getLoanProduct())
                        .thenComparing(r -> r.getId().toString()))
                .toList();

        List<CategoryCriteria> forOverlap = new ArrayList<>();
        List<SeedCandidateView> raw = new ArrayList<>();

        for (UnderwritingRuleSet rs : active) {
            String borrower = rs.getBorrowerType() == null || rs.getBorrowerType().isBlank()
                    ? MatchWildcard.ANY
                    : rs.getBorrowerType().trim().toUpperCase(Locale.ROOT);
            String product = rs.getLoanProduct() == null || rs.getLoanProduct().isBlank()
                    ? MatchWildcard.ANY
                    : rs.getLoanProduct().trim();
            // Rule sets have no intake_segment; live UW ignores intake → ANY with review note
            String intake = MatchWildcard.ANY;

            Map<String, Object> inference = new LinkedHashMap<>();
            inference.put("borrowerType", Map.of("source", "underwriting_rule_sets.borrower_type", "value", borrower));
            inference.put("loanProduct", Map.of("source", "underwriting_rule_sets.loan_product", "value", product));
            inference.put("intakeSegment", Map.of(
                    "source", "ABSENT_ON_RULE_SET",
                    "value", MatchWildcard.ANY,
                    "note", "Live rule-set matching does not filter intakeSegment; seeded as ANY"));
            Map<String, Object> amountInf = new LinkedHashMap<>();
            amountInf.put("source", "underwriting_rule_sets.min_amount/max_amount");
            amountInf.put("min", rs.getMinAmount()); // null = unbounded
            amountInf.put("max", rs.getMaxAmount());
            inference.put("amount", amountInf);

            Optional<UnderwritingScorecard> scorecard = findUniqueActiveScorecard(
                    rs.getBorrowerType(), rs.getLoanProduct());
            if (scorecard.isEmpty()) {
                inference.put("scorecard", Map.of(
                        "status", "NEEDS_REVIEW",
                        "note", "No unique ACTIVE scorecard for borrower+product"));
            } else {
                inference.put("scorecard", Map.of(
                        "source", "underwriting_scorecards unique active match",
                        "scorecardId", scorecard.get().getId().toString()));
            }

            String ccCode = categoryCode(rs.getId());
            String psCode = policySetCode(rs.getId());
            boolean seeded = categoryRepository.findByCodeAndVersionNo(ccCode, 1).isPresent()
                    || policySetRepository.findByCodeAndVersionNo(psCode, 1).isPresent();

            String amountRange = formatAmountRange(rs.getMinAmount(), rs.getMaxAmount());
            String name = displayName(borrower, product, intake, amountRange);

            SeedCandidateView view = new SeedCandidateView(
                    ccCode,
                    name,
                    borrower,
                    product,
                    intake,
                    amountRange,
                    psCode,
                    "Policy Set · " + rs.getName(),
                    List.of(rs.getId()),
                    scorecard.map(UnderwritingScorecard::getId).orElse(null),
                    scorecard.map(UnderwritingScorecard::getName).orElse(null),
                    rs.getId(),
                    rs.getName(),
                    REVIEW_REQUIRED,
                    inference,
                    List.of(),
                    seeded);
            raw.add(view);
            forOverlap.add(new CategoryCriteria(
                    ccCode, name, borrower, product, intake, rs.getMinAmount(), rs.getMaxAmount()));
        }

        List<OverlapWarning> overlaps = CustomerCategoryOverlapDetector.findOverlaps(forOverlap);
        List<SeedCandidateView> withOverlaps = new ArrayList<>();
        for (SeedCandidateView c : raw) {
            List<Map<String, Object>> mine = overlaps.stream()
                    .filter(o -> c.categoryCode().equals(o.leftIdOrCode())
                            || c.categoryCode().equals(o.rightIdOrCode()))
                    .map(this::overlapMap)
                    .toList();
            withOverlaps.add(new SeedCandidateView(
                    c.categoryCode(), c.categoryName(), c.borrowerType(), c.loanProduct(),
                    c.intakeSegment(), c.amountRange(), c.policySetCode(), c.policySetName(),
                    c.ruleSetIds(), c.scorecardId(), c.scorecardName(), c.sourceRuleSetId(),
                    c.sourceRuleSetName(), c.reviewStatus(), c.inferenceSource(), mine, c.alreadySeeded()));
        }
        return withOverlaps;
    }

    private Optional<UnderwritingScorecard> findUniqueActiveScorecard(String borrowerType, String loanProduct) {
        if (borrowerType == null || loanProduct == null) {
            return Optional.empty();
        }
        List<UnderwritingScorecard> list = scorecardRepository
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(borrowerType, loanProduct);
        if (list.size() == 1) {
            return Optional.of(list.get(0));
        }
        return Optional.empty();
    }

    static String categoryCode(UUID ruleSetId) {
        return "CC_SEED_" + ruleSetId.toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    static String policySetCode(UUID ruleSetId) {
        return "PS_SEED_" + ruleSetId.toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    static String formatAmountRange(BigDecimal min, BigDecimal max) {
        String lo = min == null ? "UNBOUNDED" : min.toPlainString();
        String hi = max == null ? "UNBOUNDED" : max.toPlainString();
        return lo + " .. " + hi;
    }

    static String displayName(String borrower, String product, String intake, String amountRange) {
        return borrower + " · " + product + " · " + intake + " · " + amountRange;
    }

    private Map<String, Object> overlapMap(OverlapWarning o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", "WARNING");
        m.put("left", o.leftIdOrCode());
        m.put("right", o.rightIdOrCode());
        m.put("reasons", o.reasons());
        m.put("evidence", o.evidence());
        return m;
    }

    private static BigDecimal parseMin(String amountRange) {
        if (amountRange == null) {
            return null;
        }
        String left = amountRange.split("\\.\\.")[0].trim();
        if ("UNBOUNDED".equalsIgnoreCase(left)) {
            return null;
        }
        return new BigDecimal(left);
    }

    private static BigDecimal parseMax(String amountRange) {
        if (amountRange == null) {
            return null;
        }
        String[] parts = amountRange.split("\\.\\.");
        if (parts.length < 2) {
            return null;
        }
        String right = parts[1].trim();
        if ("UNBOUNDED".equalsIgnoreCase(right)) {
            return null;
        }
        return new BigDecimal(right);
    }
}
