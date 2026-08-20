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
import com.los.core.customercategory.selection.CategoryPropositionConfig;
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
 * Customer Category governance — lending proposition config; not wired to live UW.
 * STEP-2: principal underwriting relation = Policy Studio Policy Version (config only).
 * W2: independent Category → exact Workflow Version bind (config only; no live routing).
 */
@Service
@RequiredArgsConstructor
public class CustomerCategoryService {

    private final CustomerCategoryRepository repository;
    private final PolicySetRepository policySetRepository;
    private final CustomerCategoryValidator validator;
    private final AdminConfigAuditSupport auditSupport;
    private final CategoryPolicyBindService policyBindService;
    private final CategoryWorkflowBindService workflowBindService;
    private final com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository
            applicabilityRepository;

    @Transactional(readOnly = true)
    public List<CustomerCategoryEntity> listEntitiesForCompatibilityScan() {
        return repository.findAllByOrderByCodeAscVersionNoDesc();
    }

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

        // Principal underwriting relation — Policy Studio Policy Version
        checks.addAll(policyBindService.policyActivationChecks(e));
        // W2 — independent Workflow Version bind (config readiness only)
        checks.addAll(workflowBindService.workflowActivationChecks(e));

        // Transitional Policy Set package — informational when present; not required for new Categories
        if (e.getPolicySetId() != null) {
            PolicySetEntity ps = policySetRepository.findById(e.getPolicySetId()).orElse(null);
            boolean psOk = ps != null && ps.getStatus() == ConfigLifecycleStatus.ACTIVE;
            checks.add(new ActivationCheck(
                    "POLICY_SET_TRANSITIONAL",
                    "Transitional Policy Set package (internal; not lender-facing principal)",
                    psOk || ps == null,
                    ps == null ? "policy_set_id set but row missing"
                            : "Policy Set status: " + ps.getStatus().name()
                            + " — Category activation principal check is Policy Version, not Policy Set"));
            if (ps != null) {
                checks.add(runCheck("RULE_SET_READY_TRANSITIONAL", "Transitional primary rule set executable",
                        () -> validator.requireLiveReadyRuleSet(ps.getPrimaryRuleSetId())));
            }
        } else {
            checks.add(new ActivationCheck(
                    "POLICY_SET_NOT_REQUIRED",
                    "Policy Set not required for Category composition",
                    true,
                    "OK — Policy Set demoted; Policy Version is principal"));
        }

        checks.add(runCheck("MATCH_DIMENSIONS", "Entity Type / product / Customer Role / amount valid",
                () -> validator.validateMatchDimensions(
                        e.getBorrowerType(), e.getLoanProduct(), e.getIntakeSegment(),
                        e.getMinAmount(), e.getMaxAmount())));
        checks.add(runCheck("EFFECTIVE_DATES", "Effective dates valid",
                () -> validator.validateEffectiveDates(e.getEffectiveFrom(), e.getEffectiveUntil())));

        // Ready for future activation only when Policy + Workflow links OK (Policy Set no longer required)
        boolean ready = checks.stream()
                .filter(c -> !"POLICY_SET_TRANSITIONAL".equals(c.code())
                        && !"RULE_SET_READY_TRANSITIONAL".equals(c.code()))
                .allMatch(ActivationCheck::ok);
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
        String creditVintage = normalizeCreditVintage(req.creditVintage());
        validator.validateAmountRange(req.minAmount(), req.maxAmount());
        validator.validateEffectiveDates(req.effectiveFrom(), req.effectiveUntil());

        CategoryPolicyBindService.ResolvedPolicyBind policyBind = null;
        if (req.policyApplicabilityId() != null) {
            policyBind = policyBindService.resolveBind(
                    req.policyApplicabilityId(), req.policyDocumentId(), req.policyVersionLabel());
            policyBindService.requireScopeCompatible(
                    new CustomerCategoryPolicyScopeCompatibility.CategoryScope(
                            intake, borrower, product, req.minAmount(), req.maxAmount(),
                            req.effectiveFrom(), req.effectiveUntil(), creditVintage),
                    req.policyApplicabilityId());
        }

        CategoryWorkflowBindService.ResolvedWorkflowBind workflowBind = null;
        if (req.workflowId() != null) {
            workflowBind = workflowBindService.resolveBind(req.workflowId(), req.workflowVersion());
            workflowBindService.requireCompatible(intake, borrower, product, creditVintage, req.workflowId());
        }

        UUID transitionalPsId = null;
        if (req.policySetId() != null) {
            PolicySetEntity ps = policySetRepository.findById(req.policySetId())
                    .orElseThrow(() -> CustomerCategoryValidator.biz("Policy Set not found",
                            "POLICY_SET_NOT_FOUND", Map.of("policySetId", req.policySetId().toString())));
            transitionalPsId = ps.getId();
        }

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
                .creditVintage(creditVintage)
                .minAmount(req.minAmount())
                .maxAmount(req.maxAmount())
                .policySetId(transitionalPsId)
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
        if (policyBind != null) {
            policyBindService.applyBind(e, policyBind);
        }
        if (workflowBind != null) {
            workflowBindService.applyBind(e, workflowBind);
        }
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
            if (matchingChanged(e, req) || effectiveChanged(e, req) || policyChanged(e, req)
                    || workflowChanged(e, req)) {
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
        if (req.creditVintage() != null) {
            e.setCreditVintage(normalizeCreditVintage(req.creditVintage()));
        }
        e.setMinAmount(req.minAmount());
        e.setMaxAmount(req.maxAmount());
        validator.validateAmountRange(e.getMinAmount(), e.getMaxAmount());
        if (req.policySetId() != null) {
            PolicySetEntity ps = policySetRepository.findById(req.policySetId())
                    .orElseThrow(() -> CustomerCategoryValidator.biz("Policy Set not found",
                            "POLICY_SET_NOT_FOUND", Map.of("policySetId", req.policySetId().toString())));
            e.setPolicySetId(ps.getId());
        }
        applyPendingPolicyBind(e, req.policyApplicabilityId(), req.policyDocumentId(), req.policyVersionLabel(),
                req.effectiveFrom(), req.effectiveUntil());
        // Re-resolving an already-linked Workflow fails when the live catalogue
        // version number moved (stale pin). Policy bind is independent — skip.
        if (req.workflowId() != null && !req.workflowId().equals(e.getWorkflowId())) {
            CategoryWorkflowBindService.ResolvedWorkflowBind wb = workflowBindService.resolveBind(
                    req.workflowId(), req.workflowVersion());
            workflowBindService.requireCompatible(
                    e.getIntakeSegment(), e.getBorrowerType(), e.getLoanProduct(), e.getCreditVintage(),
                    req.workflowId());
            workflowBindService.applyBind(e, wb);
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
        if (body != null && body.policyApplicabilityId() != null) {
            applyPendingPolicyBind(e, body.policyApplicabilityId(), body.policyDocumentId(),
                    body.policyVersionLabel(), null, null);
        }
        if (!"LINKED".equals(CategoryPolicyBindService.linkageStatus(e))) {
            throw CustomerCategoryValidator.biz(
                    "Select a Policy Studio Policy Version and save, or Submit with that selection.",
                    CategoryPolicyBindService.LINKAGE_REQUIRED,
                    Map.of("id", e.getId().toString(), "code", e.getCode(),
                            "policyApplicabilityId", e.getPolicyApplicabilityId() == null ? "" : e.getPolicyApplicabilityId().toString()));
        }
        // Re-validate catalogue row still exists + scope still covers Category
        policyBindService.requireApplicability(e.getPolicyApplicabilityId());
        policyBindService.requireScopeCompatible(
                CustomerCategoryPolicyScopeCompatibility.CategoryScope.fromEntity(e),
                e.getPolicyApplicabilityId());
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
        if (!"LINKED".equals(CategoryPolicyBindService.linkageStatus(e))) {
            throw CustomerCategoryValidator.biz(
                    "POLICY LINKAGE REQUIRED before activation",
                    CategoryPolicyBindService.LINKAGE_REQUIRED, Map.of("id", id.toString()));
        }
        if (!"LINKED".equals(CategoryWorkflowBindService.linkageStatus(e))) {
            throw CustomerCategoryValidator.biz(
                    "WORKFLOW LINKAGE REQUIRED before activation",
                    CategoryWorkflowBindService.LINKAGE_REQUIRED, Map.of("id", id.toString()));
        }
        for (ActivationCheck c : policyBindService.policyActivationChecks(e)) {
            if (!c.ok() && List.of("POLICY_SELECTED", "POLICY_VERSION_RESOLVABLE",
                    "POLICY_LIFECYCLE_OK", "POLICY_NOT_DEPRECATED", "POLICY_READINESS_OK",
                    "POLICY_SCOPE_COMPATIBLE").contains(c.code())) {
                throw CustomerCategoryValidator.biz(c.detail() == null ? c.label() : c.detail(),
                        c.code(), Map.of("id", id.toString()));
            }
        }
        for (ActivationCheck c : workflowBindService.workflowActivationChecks(e)) {
            if (!c.ok() && List.of("WORKFLOW_SELECTED", "WORKFLOW_VERSION_EXISTS",
                    "WORKFLOW_ELIGIBLE", "WORKFLOW_APPLICABILITY_COMPATIBLE",
                    "WORKFLOW_CONTENT_IDENTITY_VALID",
                    CategoryWorkflowBindService.WORKFLOW_VERSION_MUTATED).contains(c.code())) {
                throw CustomerCategoryValidator.biz(c.detail() == null ? c.label() : c.detail(),
                        c.code(), Map.of("id", id.toString()));
            }
        }
        // Category activation does NOT make Policy live / production-authoritative.
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
                .policyApplicabilityId(src.getPolicyApplicabilityId())
                .policyDocumentId(src.getPolicyDocumentId())
                .policyVersionLabel(src.getPolicyVersionLabel())
                .policyLineageId(src.getPolicyLineageId())
                .workflowId(src.getWorkflowId())
                .workflowVersion(src.getWorkflowVersion())
                .workflowContentHash(src.getWorkflowContentHash())
                .workflowName(src.getWorkflowName())
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

    /**
     * Customer-facing proposition + SAFE disambiguation attributes (governanceJson).
     * Allowed on DRAFT/APPROVED; does not activate or change matching criteria.
     */
    @Transactional
    public Map<String, Object> updatePropositionConfig(UUID id, Map<String, Object> body) {
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() == ConfigLifecycleStatus.RETIRED) {
            throw CustomerCategoryValidator.biz("RETIRED category cannot be edited",
                    "CATEGORY_RETIRED", Map.of("id", id.toString()));
        }
        if (e.getStatus() == ConfigLifecycleStatus.ACTIVE) {
            // Proposition + SAFE disambiguation only — does not change matching dimensions
            Map<String, Object> limited = new LinkedHashMap<>();
            if (body.get("proposition") instanceof Map<?, ?> p) {
                Map<String, Object> copy = new LinkedHashMap<>();
                p.forEach((k, v) -> copy.put(String.valueOf(k), v));
                limited.put("proposition", copy);
            }
            if (body.get("disambiguation") instanceof Map<?, ?> d) {
                Map<String, Object> copy = new LinkedHashMap<>();
                d.forEach((k, v) -> copy.put(String.valueOf(k), v));
                limited.put("disambiguation", copy);
            }
            CategoryPropositionConfig.putPropositionConfig(e, limited);
        } else {
            CategoryPropositionConfig.putPropositionConfig(e, body != null ? body : Map.of());
        }
        repository.save(e);
        return Map.of(
                "categoryId", e.getId(),
                "code", e.getCode(),
                "versionNo", e.getVersionNo(),
                "status", e.getStatus().name(),
                "governanceJson", e.getGovernanceJson() != null ? e.getGovernanceJson() : Map.of());
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
                CreditTerminologyCompatibility.toCustomerRoleAlias(e.getIntakeSegment()),
                e.getPolicyApplicabilityId(),
                e.getPolicyDocumentId(),
                e.getPolicyVersionLabel(),
                e.getPolicyLineageId(),
                policyDisplayName(e),
                policyBusinessStatus(e),
                CategoryPolicyBindService.linkageStatus(e),
                e.getWorkflowId(),
                e.getWorkflowVersion(),
                e.getWorkflowContentHash(),
                e.getWorkflowName(),
                CategoryWorkflowBindService.linkageStatus(e),
                e.getCreditVintage());
    }

    private String policyDisplayName(CustomerCategoryEntity e) {
        if (e.getPolicyApplicabilityId() == null) {
            return null;
        }
        return applicabilityRepository.findById(e.getPolicyApplicabilityId())
                .map(a -> a.getPolicyName())
                .orElse(null);
    }

    private String policyBusinessStatus(CustomerCategoryEntity e) {
        if (e.getPolicyApplicabilityId() == null) {
            return null;
        }
        return applicabilityRepository.findById(e.getPolicyApplicabilityId())
                .map(a -> a.getBusinessStatus())
                .orElse(null);
    }

    private Map<String, Object> overlapToMap(OverlapWarning o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", "WARNING");
        m.put("kind", "ALSO_ELIGIBLE_PROPOSITIONS");
        m.put("label", "Also eligible propositions");
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
        m.put("policySetId", e.getPolicySetId() == null ? null : e.getPolicySetId().toString());
        m.put("policyApplicabilityId",
                e.getPolicyApplicabilityId() == null ? null : e.getPolicyApplicabilityId().toString());
        m.put("policyDocumentId", e.getPolicyDocumentId() == null ? null : e.getPolicyDocumentId().toString());
        m.put("policyVersionLabel", e.getPolicyVersionLabel());
        m.put("policyLinkageStatus", CategoryPolicyBindService.linkageStatus(e));
        m.put("workflowId", e.getWorkflowId() == null ? null : e.getWorkflowId().toString());
        m.put("workflowVersion", e.getWorkflowVersion());
        m.put("workflowContentHash", e.getWorkflowContentHash());
        m.put("workflowName", e.getWorkflowName());
        m.put("workflowLinkageStatus", CategoryWorkflowBindService.linkageStatus(e));
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
        if (req.creditVintage() != null && !req.creditVintage().equalsIgnoreCase(e.getCreditVintage())
                && !(MatchWildcard.isAny(req.creditVintage()) && MatchWildcard.isAny(e.getCreditVintage()))) {
            return true;
        }
        return false;
    }

    private static String normalizeCreditVintage(String creditVintage) {
        return creditVintage == null || creditVintage.isBlank()
                ? MatchWildcard.ANY
                : creditVintage.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Persist the exact Policy Studio applicability selection onto the category row.
     * Overwrites the same columns — never creates a second link row.
     */
    private void applyPendingPolicyBind(
            CustomerCategoryEntity e,
            UUID policyApplicabilityId,
            UUID policyDocumentId,
            String policyVersionLabel,
            Instant requestFrom,
            Instant requestUntil) {
        if (policyApplicabilityId == null) {
            return;
        }
        CategoryPolicyBindService.ResolvedPolicyBind bind = policyBindService.resolveBind(
                policyApplicabilityId, policyDocumentId, policyVersionLabel);
        Instant from = requestFrom != null ? requestFrom : e.getEffectiveFrom();
        Instant until = requestUntil != null ? requestUntil : e.getEffectiveUntil();
        policyBindService.requireScopeCompatible(
                new CustomerCategoryPolicyScopeCompatibility.CategoryScope(
                        e.getIntakeSegment(), e.getBorrowerType(), e.getLoanProduct(),
                        e.getMinAmount(), e.getMaxAmount(), from, until, e.getCreditVintage()),
                policyApplicabilityId);
        policyBindService.applyBind(e, bind);
    }

    private static boolean policyChanged(CustomerCategoryEntity e, CategoryRequest req) {
        if (req == null) {
            return false;
        }
        if (req.policySetId() != null && !req.policySetId().equals(e.getPolicySetId())) {
            return true;
        }
        if (req.policyApplicabilityId() != null
                && !req.policyApplicabilityId().equals(e.getPolicyApplicabilityId())) {
            return true;
        }
        if (req.policyDocumentId() != null && !req.policyDocumentId().equals(e.getPolicyDocumentId())) {
            return true;
        }
        if (req.policyVersionLabel() != null && !req.policyVersionLabel().isBlank()
                && !req.policyVersionLabel().trim().equalsIgnoreCase(
                e.getPolicyVersionLabel() == null ? "" : e.getPolicyVersionLabel().trim())) {
            return true;
        }
        return false;
    }

    private static boolean workflowChanged(CustomerCategoryEntity e, CategoryRequest req) {
        if (req == null) {
            return false;
        }
        if (req.workflowId() != null && !req.workflowId().equals(e.getWorkflowId())) {
            return true;
        }
        if (req.workflowVersion() != null
                && !req.workflowVersion().equals(e.getWorkflowVersion())) {
            return true;
        }
        return false;
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
