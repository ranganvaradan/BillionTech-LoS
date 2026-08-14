package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.customercategory.CustomerCategoryDtos.ActivationCheck;
import com.los.core.customercategory.CustomerCategoryDtos.ActivationReadinessResponse;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.customercategory.CustomerCategoryDtos.LifecycleActionRequest;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.CategoryCriteria;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.OverlapWarning;
import com.los.core.domain.CreditTerminologyCompatibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Category governance — routing criteria only; not wired to live UW.
 */
@Service
@RequiredArgsConstructor
public class CustomerCategoryService {

    private final CustomerCategoryRepository repository;
    private final PolicySetRepository policySetRepository;
    private final CustomerCategoryValidator validator;
    private final AdminConfigAuditSupport auditSupport;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        List<CustomerCategoryEntity> all = repository.findAllByOrderByCodeAscVersionNoDesc();
        List<OverlapWarning> overlaps = detectOverlapsAmong(relevantForOverlap());
        return all.stream().map(c -> toResponse(c, overlaps)).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return toResponse(load(id), detectOverlapsAmong(relevantForOverlap()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID id) {
        return ConfigGovernanceHistory.historyView(load(id).getGovernanceJson());
    }

    /**
     * Non-mutating activation readiness — reuses activate validators; does not change status.
     */
    @Transactional(readOnly = true)
    public ActivationReadinessResponse activationReadiness(UUID id) {
        CustomerCategoryEntity e = load(id);
        List<ActivationCheck> checks = new ArrayList<>();
        List<OverlapWarning> overlaps = detectOverlapsAmong(relevantForOverlap());
        List<Map<String, Object>> mine = overlaps.stream()
                .filter(o -> {
                    String key = e.getCode() + "@v" + e.getVersionNo();
                    return key.equals(o.leftIdOrCode()) || key.equals(o.rightIdOrCode());
                })
                .map(this::overlapToMap)
                .toList();

        boolean approved = e.getStatus() == ConfigLifecycleStatus.APPROVED;
        checks.add(new ActivationCheck(
                "STATUS_APPROVED",
                "Category is APPROVED",
                approved,
                approved ? "APPROVED" : "Current status: " + e.getStatus().name()));

        PolicySetEntity ps = policySetRepository.findById(e.getPolicySetId()).orElse(null);
        boolean psOk = ps != null && ps.getStatus() == ConfigLifecycleStatus.ACTIVE;
        checks.add(new ActivationCheck(
                "POLICY_SET_ACTIVE",
                "Linked Policy Set is ACTIVE",
                psOk,
                ps == null ? "Policy Set missing"
                        : "Policy Set status: " + ps.getStatus().name()));

        if (ps != null) {
            checks.add(runCheck("RULE_SET_READY", "Primary rule set executable",
                    () -> validator.requireLiveReadyRuleSet(ps.getPrimaryRuleSetId())));
            checks.add(runCheck("SINGLE_RULE_SET", "Phase-1 single rule set",
                    () -> validator.requireSingleRuleSetComposition(ps.getAdditionalRuleSetIds())));
            checks.add(runCheck("SCORECARD_READY", "Scorecard executable",
                    () -> validator.requireExecutableScorecard(ps.getScorecardId())));
        }

        checks.add(runCheck("MATCH_DIMENSIONS", "Borrower / product / intake / amount valid",
                () -> validator.validateMatchDimensions(
                        e.getBorrowerType(), e.getLoanProduct(), e.getIntakeSegment(),
                        e.getMinAmount(), e.getMaxAmount())));
        checks.add(runCheck("EFFECTIVE_DATES", "Effective dates valid",
                () -> validator.validateEffectiveDates(e.getEffectiveFrom(), e.getEffectiveUntil())));

        boolean ready = checks.stream().allMatch(ActivationCheck::ok);
        return new ActivationReadinessResponse(
                e.getId(), "CUSTOMER_CATEGORY", e.getStatus().name(), ready, checks, mine);
    }

    private static ActivationCheck runCheck(String code, String label, Runnable action) {
        try {
            action.run();
            return new ActivationCheck(code, label, true, "OK");
        } catch (BusinessRuleException ex) {
            return new ActivationCheck(code, label, false,
                    ex.getMessage() == null ? ex.getReason() : ex.getMessage());
        } catch (RuntimeException ex) {
            return new ActivationCheck(code, label, false, ex.getMessage());
        }
    }

    @Transactional
    public CategoryResponse createDraft(CategoryRequest req, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        if (req == null || req.code() == null || req.code().isBlank()) {
            throw CustomerCategoryValidator.biz("code required", "CATEGORY_CODE_REQUIRED", Map.of());
        }
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.findByCodeAndVersionNo(code, 1).isPresent()) {
            throw CustomerCategoryValidator.biz("Category code already exists at version 1: " + code,
                    "CATEGORY_CODE_EXISTS", Map.of("code", code));
        }
        String borrower = validator.normalizeBorrowerType(
                CreditTerminologyCompatibility.resolveEntityTypeForStorage(req.entityType(), req.borrowerType()));
        String product = validator.normalizeLoanProduct(req.loanProduct());
        String intake = validator.normalizeIntakeSegment(
                CreditTerminologyCompatibility.resolveCustomerRoleForStorage(req.customerRole(), req.intakeSegment()));
        validator.validateAmountRange(req.minAmount(), req.maxAmount());
        validator.validateEffectiveDates(req.effectiveFrom(), req.effectiveUntil());
        if (req.policySetId() == null) {
            throw CustomerCategoryValidator.biz("policySetId required (exactly one Policy Set)",
                    "POLICY_SET_REQUIRED", Map.of());
        }
        PolicySetEntity ps = policySetRepository.findById(req.policySetId())
                .orElseThrow(() -> CustomerCategoryValidator.biz("Policy Set not found",
                        "POLICY_SET_NOT_FOUND", Map.of("policySetId", req.policySetId().toString())));

        CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID())
                .code(code)
                .versionNo(1)
                .name(requireName(req.name()))
                .description(req.description())
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType(borrower)
                .loanProduct(product)
                .intakeSegment(intake)
                .minAmount(req.minAmount())
                .maxAmount(req.maxAmount())
                .policySetId(ps.getId())
                .effectiveFrom(req.effectiveFrom())
                .effectiveUntil(req.effectiveUntil())
                .reasonForChange(req.reasonForChange())
                .reviewStatus("DRAFT")
                .inferenceNotes(new LinkedHashMap<>())
                .governanceJson(new LinkedHashMap<>())
                .createdAt(Instant.now())
                .createdBy(actor.identity())
                .updatedBy(actor.identity())
                .build();
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "CREATED", actor, req.reasonForChange());
        repository.save(e);
        auditSupport.captureCreate("CUSTOMER_CATEGORY", e.getId().toString(), snapshot(e),
                "Create DRAFT Customer Category");
        return toResponse(e, detectOverlapsAmong(relevantForOverlap()));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest req, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        CustomerCategoryEntity e = load(id);
        Map<String, Object> before = snapshot(e);
        if (e.getStatus() == ConfigLifecycleStatus.RETIRED) {
            throw CustomerCategoryValidator.biz("RETIRED category cannot be edited",
                    "CATEGORY_RETIRED", Map.of("id", id.toString()));
        }
        if (e.getStatus() == ConfigLifecycleStatus.ACTIVE
                || e.getStatus() == ConfigLifecycleStatus.IN_REVIEW
                || e.getStatus() == ConfigLifecycleStatus.APPROVED) {
            if (matchingChanged(e, req) || effectiveChanged(e, req) || policyChanged(e, req)) {
                throw CustomerCategoryValidator.biz(
                        "Matching criteria cannot be mutated in place for " + e.getStatus()
                                + "; create a new version",
                        "ACTIVE_CATEGORY_IMMUTABLE",
                        Map.of("id", id.toString(), "code", e.getCode(), "status", e.getStatus().name()));
            }
            // non-matching cosmetic only for ACTIVE
            if (e.getStatus() == ConfigLifecycleStatus.ACTIVE) {
                if (req.name() != null && !req.name().isBlank()) {
                    e.setName(req.name().trim());
                }
                if (req.description() != null) {
                    e.setDescription(req.description());
                }
                e.setUpdatedBy(actor.identity());
                repository.save(e);
                auditSupport.captureUpdate("CUSTOMER_CATEGORY", e.getId().toString(), before, snapshot(e),
                        "Update ACTIVE category non-matching fields");
                return get(e.getId());
            }
            throw CustomerCategoryValidator.biz("Only DRAFT category matching fields can be edited",
                    "CATEGORY_NOT_DRAFT", Map.of("status", e.getStatus().name()));
        }
        // DRAFT
        e.setName(requireName(req.name() != null ? req.name() : e.getName()));
        if (req.description() != null) {
            e.setDescription(req.description());
        }
        String resolvedEntity = CreditTerminologyCompatibility.resolveEntityTypeForStorage(
                req.entityType(), req.borrowerType());
        if (resolvedEntity != null) {
            e.setBorrowerType(validator.normalizeBorrowerType(resolvedEntity));
        }
        if (req.loanProduct() != null) {
            e.setLoanProduct(validator.normalizeLoanProduct(req.loanProduct()));
        }
        String resolvedRole = CreditTerminologyCompatibility.resolveCustomerRoleForStorage(
                req.customerRole(), req.intakeSegment());
        if (resolvedRole != null) {
            e.setIntakeSegment(validator.normalizeIntakeSegment(resolvedRole));
        }
        e.setMinAmount(req.minAmount());
        e.setMaxAmount(req.maxAmount());
        validator.validateAmountRange(e.getMinAmount(), e.getMaxAmount());
        if (req.policySetId() != null) {
            if (!policySetRepository.existsById(req.policySetId())) {
                throw CustomerCategoryValidator.biz("Policy Set not found",
                        "POLICY_SET_NOT_FOUND", Map.of("policySetId", req.policySetId().toString()));
            }
            e.setPolicySetId(req.policySetId());
        }
        Instant from = req.effectiveFrom() != null ? req.effectiveFrom() : e.getEffectiveFrom();
        Instant until = req.effectiveUntil() != null ? req.effectiveUntil() : e.getEffectiveUntil();
        validator.validateEffectiveDates(from, until);
        if (req.effectiveFrom() != null) {
            e.setEffectiveFrom(req.effectiveFrom());
        }
        if (req.effectiveUntil() != null) {
            e.setEffectiveUntil(req.effectiveUntil());
        }
        if (req.reasonForChange() != null) {
            e.setReasonForChange(req.reasonForChange());
        }
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "UPDATED", actor, req.reasonForChange());
        repository.save(e);
        auditSupport.captureUpdate("CUSTOMER_CATEGORY", e.getId().toString(), before, snapshot(e),
                "Update DRAFT Customer Category");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse submit(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.DRAFT) {
            throw CustomerCategoryValidator.biz("Only DRAFT category can be submitted",
                    "CATEGORY_NOT_DRAFT", Map.of("status", e.getStatus().name()));
        }
        validator.validateMatchDimensions(
                e.getBorrowerType(), e.getLoanProduct(), e.getIntakeSegment(),
                e.getMinAmount(), e.getMaxAmount());
        validator.validateEffectiveDates(e.getEffectiveFrom(), e.getEffectiveUntil());
        if (!policySetRepository.existsById(e.getPolicySetId())) {
            throw CustomerCategoryValidator.biz("Policy Set missing", "POLICY_SET_NOT_FOUND", Map.of());
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.IN_REVIEW);
        e.setReviewStatus("IN_REVIEW");
        e.setSubmittedBy(actor.identity());
        e.setSubmittedAt(Instant.now());
        e.setUpdatedBy(actor.identity());
        e.getGovernanceJson().put("submittedByUserId", actor.userId());
        e.getGovernanceJson().put("overlapAtSubmit", overlapReport());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "SUBMITTED", actor,
                body == null ? null : body.remarks());
        repository.save(e);
        auditSupport.captureAction("CUSTOMER_CATEGORY", e.getId().toString(), "SUBMIT", before, snapshot(e),
                "Submit Customer Category for review");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse approve(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireChecker(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.IN_REVIEW) {
            throw CustomerCategoryValidator.biz("Only IN_REVIEW category can be approved",
                    "CATEGORY_NOT_IN_REVIEW", Map.of("status", e.getStatus().name()));
        }
        Actor submitter = new Actor(
                str(e.getGovernanceJson().get("submittedByUserId")),
                e.getSubmittedBy(),
                null);
        ConfigGovernanceRoles.forbidSelfApproval(submitter, actor);
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.APPROVED);
        e.setReviewStatus("APPROVED");
        e.setApprovedBy(actor.identity());
        e.setApprovedAt(Instant.now());
        e.setUpdatedBy(actor.identity());
        e.getGovernanceJson().put("overlapAtApprove", overlapReport());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "APPROVED", actor,
                body == null ? null : body.remarks());
        repository.save(e);
        auditSupport.captureAction("CUSTOMER_CATEGORY", e.getId().toString(), "APPROVE", before, snapshot(e),
                "Approve Customer Category");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse returnToDraft(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireChecker(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.IN_REVIEW
                && e.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz("Only IN_REVIEW or APPROVED category can be returned",
                    "CATEGORY_NOT_RETURNABLE", Map.of("status", e.getStatus().name()));
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.DRAFT);
        e.setReviewStatus("DRAFT");
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "RETURNED", actor,
                body == null ? null : body.remarks());
        repository.save(e);
        auditSupport.captureAction("CUSTOMER_CATEGORY", e.getId().toString(), "RETURN", before, snapshot(e),
                "Return Customer Category to DRAFT");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse activate(UUID id, Actor actor) {
        ConfigGovernanceRoles.requireActivator(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz(
                    "Only APPROVED category can be activated (DRAFT→ACTIVE not allowed)",
                    "CATEGORY_NOT_APPROVED", Map.of("status", e.getStatus().name()));
        }
        PolicySetEntity ps = policySetRepository.findById(e.getPolicySetId())
                .orElseThrow(() -> CustomerCategoryValidator.biz("Policy Set missing",
                        "POLICY_SET_NOT_FOUND", Map.of()));
        if (ps.getStatus() != ConfigLifecycleStatus.ACTIVE) {
            throw CustomerCategoryValidator.biz("Policy Set must be ACTIVE before category activation",
                    "POLICY_SET_NOT_READY", Map.of("policySetStatus", ps.getStatus().name()));
        }
        validator.requireLiveReadyRuleSet(ps.getPrimaryRuleSetId());
        validator.requireSingleRuleSetComposition(ps.getAdditionalRuleSetIds());
        validator.requireExecutableScorecard(ps.getScorecardId());
        validator.validateMatchDimensions(
                e.getBorrowerType(), e.getLoanProduct(), e.getIntakeSegment(),
                e.getMinAmount(), e.getMaxAmount());
        validator.validateEffectiveDates(e.getEffectiveFrom(), e.getEffectiveUntil());

        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.ACTIVE);
        e.setReviewStatus("ACTIVE");
        e.setActivatedAt(Instant.now());
        e.setActivatedBy(actor.identity());
        e.setUpdatedBy(actor.identity());
        e.getGovernanceJson().put("overlapAtActivate", overlapReport());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "ACTIVATED", actor, null);
        repository.save(e);
        auditSupport.captureAction("CUSTOMER_CATEGORY", e.getId().toString(), "ACTIVATE",
                before, snapshot(e), "Activate Customer Category (overlaps are WARNING only)");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse retire(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireActivator(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() == ConfigLifecycleStatus.RETIRED) {
            return get(id);
        }
        if (e.getStatus() != ConfigLifecycleStatus.ACTIVE && e.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz("Only ACTIVE or APPROVED category can be retired",
                    "CATEGORY_NOT_RETIRABLE", Map.of("status", e.getStatus().name()));
        }
        String reason = body == null ? null : (body.reason() != null ? body.reason() : body.remarks());
        if (reason == null || reason.isBlank()) {
            throw CustomerCategoryValidator.biz("retirement reason required", "RETIREMENT_REASON_REQUIRED", Map.of());
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.RETIRED);
        e.setReviewStatus("RETIRED");
        e.setRetiredAt(Instant.now());
        e.setRetiredBy(actor.identity());
        e.setRetirementReason(reason.trim());
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "RETIRED", actor, reason);
        repository.save(e);
        auditSupport.captureAction("CUSTOMER_CATEGORY", e.getId().toString(), "RETIRE",
                before, snapshot(e), "Retire Customer Category");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse copyVersion(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        CustomerCategoryEntity src = load(id);
        if (src.getStatus() != ConfigLifecycleStatus.ACTIVE
                && src.getStatus() != ConfigLifecycleStatus.RETIRED
                && src.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz("Copy/version allowed from ACTIVE, APPROVED, or RETIRED",
                    "CATEGORY_COPY_SOURCE_INVALID", Map.of("status", src.getStatus().name()));
        }
        int next = repository.findFirstByCodeOrderByVersionNoDesc(src.getCode())
                .map(c -> c.getVersionNo() + 1).orElse(1);
        CustomerCategoryEntity e = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID())
                .code(src.getCode())
                .versionNo(next)
                .name(src.getName())
                .description(src.getDescription())
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType(src.getBorrowerType())
                .loanProduct(src.getLoanProduct())
                .intakeSegment(src.getIntakeSegment())
                .minAmount(src.getMinAmount())
                .maxAmount(src.getMaxAmount())
                .policySetId(src.getPolicySetId())
                .seedSourceRuleSetId(src.getSeedSourceRuleSetId())
                .effectiveFrom(src.getEffectiveFrom())
                .effectiveUntil(src.getEffectiveUntil())
                .replacesCategoryId(src.getId())
                .reasonForChange(body == null ? null : body.reason())
                .reviewStatus("DRAFT")
                .inferenceNotes(new LinkedHashMap<>())
                .governanceJson(new LinkedHashMap<>())
                .createdAt(Instant.now())
                .createdBy(actor.identity())
                .updatedBy(actor.identity())
                .build();
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "COPIED", actor,
                "Copied from " + src.getId() + " v" + src.getVersionNo());
        repository.save(e);
        auditSupport.captureCreate("CUSTOMER_CATEGORY", e.getId().toString(), snapshot(e),
                "Copy Customer Category to new DRAFT version");
        return get(e.getId());
    }

    @Transactional
    public void deleteDraft(UUID id, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.DRAFT) {
            throw CustomerCategoryValidator.biz("Only DRAFT category can be deleted",
                    "CATEGORY_NOT_DRAFT", Map.of("status", e.getStatus().name()));
        }
        Map<String, Object> before = snapshot(e);
        repository.delete(e);
        auditSupport.captureDelete("CUSTOMER_CATEGORY", id.toString(), before, "Delete DRAFT Customer Category");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> overlapReport() {
        return detectOverlapsAmong(relevantForOverlap()).stream().map(this::overlapToMap).toList();
    }

    private List<CustomerCategoryEntity> relevantForOverlap() {
        return repository.findAll().stream()
                .filter(c -> c.getStatus() == ConfigLifecycleStatus.ACTIVE
                        || c.getStatus() == ConfigLifecycleStatus.DRAFT
                        || c.getStatus() == ConfigLifecycleStatus.IN_REVIEW
                        || c.getStatus() == ConfigLifecycleStatus.APPROVED)
                .toList();
    }

    List<OverlapWarning> detectOverlapsAmong(List<CustomerCategoryEntity> entities) {
        List<CategoryCriteria> criteria = new ArrayList<>();
        for (CustomerCategoryEntity e : entities) {
            criteria.add(new CategoryCriteria(
                    e.getCode() + "@v" + e.getVersionNo(),
                    e.getName(),
                    e.getBorrowerType(),
                    e.getLoanProduct(),
                    e.getIntakeSegment(),
                    e.getMinAmount(),
                    e.getMaxAmount()));
        }
        return CustomerCategoryOverlapDetector.findOverlaps(criteria);
    }

    CustomerCategoryEntity load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer Category not found: " + id));
    }

    CategoryResponse toResponse(CustomerCategoryEntity e, List<OverlapWarning> allOverlaps) {
        String key = e.getCode() + "@v" + e.getVersionNo();
        List<Map<String, Object>> mine = allOverlaps.stream()
                .filter(o -> key.equals(o.leftIdOrCode()) || key.equals(o.rightIdOrCode()))
                .map(this::overlapToMap)
                .toList();
        return new CategoryResponse(
                e.getId(),
                e.getCode(),
                e.getVersionNo(),
                e.getName(),
                e.getDescription(),
                e.getStatus().name(),
                e.getBorrowerType(),
                e.getLoanProduct(),
                e.getIntakeSegment(),
                e.getMinAmount(),
                e.getMaxAmount(),
                e.getPolicySetId(),
                e.getSeedSourceRuleSetId(),
                e.getReviewStatus(),
                e.getInferenceNotes() == null ? Map.of() : Map.copyOf(e.getInferenceNotes()),
                e.getEffectiveFrom(),
                e.getEffectiveUntil(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getCreatedBy(),
                e.getUpdatedBy(),
                e.getSubmittedBy(),
                e.getSubmittedAt(),
                e.getApprovedBy(),
                e.getApprovedAt(),
                e.getActivatedBy(),
                e.getActivatedAt(),
                e.getRetiredBy(),
                e.getRetiredAt(),
                e.getRetirementReason(),
                e.getReasonForChange(),
                e.getReplacesCategoryId(),
                mine,
                ConfigLifecycleActions.forStatus(e.getStatus()),
                ConfigGovernanceHistory.historyView(e.getGovernanceJson()),
                CreditTerminologyCompatibility.toEntityTypeAlias(e.getBorrowerType()),
                CreditTerminologyCompatibility.toCustomerRoleAlias(e.getIntakeSegment()));
    }

    private Map<String, Object> overlapToMap(OverlapWarning o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", "WARNING");
        m.put("left", o.leftIdOrCode());
        m.put("right", o.rightIdOrCode());
        m.put("leftName", o.leftName());
        m.put("rightName", o.rightName());
        m.put("reasons", o.reasons());
        m.put("evidence", o.evidence());
        return m;
    }

    static Map<String, Object> snapshot(CustomerCategoryEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("code", e.getCode());
        m.put("versionNo", e.getVersionNo());
        m.put("name", e.getName());
        m.put("status", e.getStatus().name());
        m.put("borrowerType", e.getBorrowerType());
        m.put("loanProduct", e.getLoanProduct());
        m.put("intakeSegment", e.getIntakeSegment());
        m.put("minAmount", e.getMinAmount());
        m.put("maxAmount", e.getMaxAmount());
        m.put("policySetId", e.getPolicySetId().toString());
        m.put("active", e.getStatus() == ConfigLifecycleStatus.ACTIVE);
        return m;
    }

    private static boolean matchingChanged(CustomerCategoryEntity e, CategoryRequest req) {
        if (req == null) {
            return false;
        }
        String resolvedEntity = CreditTerminologyCompatibility.resolveEntityTypeForStorage(
                req.entityType(), req.borrowerType());
        if (resolvedEntity != null && !resolvedEntity.equalsIgnoreCase(e.getBorrowerType())) {
            return true;
        }
        if (req.loanProduct() != null && !req.loanProduct().equals(e.getLoanProduct())
                && !(MatchWildcard.isAny(req.loanProduct()) && MatchWildcard.isAny(e.getLoanProduct()))) {
            return true;
        }
        String resolvedRole = CreditTerminologyCompatibility.resolveCustomerRoleForStorage(
                req.customerRole(), req.intakeSegment());
        if (resolvedRole != null && !resolvedRole.equalsIgnoreCase(e.getIntakeSegment())) {
            return true;
        }
        if (req.minAmount() != null
                && (e.getMinAmount() == null || req.minAmount().compareTo(e.getMinAmount()) != 0)) {
            return true;
        }
        if (req.maxAmount() != null
                && (e.getMaxAmount() == null || req.maxAmount().compareTo(e.getMaxAmount()) != 0)) {
            return true;
        }
        return false;
    }

    private static boolean policyChanged(CustomerCategoryEntity e, CategoryRequest req) {
        return req != null && req.policySetId() != null && !req.policySetId().equals(e.getPolicySetId());
    }

    private static boolean effectiveChanged(CustomerCategoryEntity e, CategoryRequest req) {
        if (req == null) {
            return false;
        }
        if (req.effectiveFrom() != null && !req.effectiveFrom().equals(e.getEffectiveFrom())) {
            return true;
        }
        return req.effectiveUntil() != null && !req.effectiveUntil().equals(e.getEffectiveUntil());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw CustomerCategoryValidator.biz("name required", "CATEGORY_NAME_REQUIRED", Map.of());
        }
        return name.trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
