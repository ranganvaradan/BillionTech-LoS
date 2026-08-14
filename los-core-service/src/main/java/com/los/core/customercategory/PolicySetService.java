package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.customercategory.CustomerCategoryDtos.ActivationCheck;
import com.los.core.customercategory.CustomerCategoryDtos.ActivationReadinessResponse;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.LifecycleActionRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetResponse;
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
 * Policy Set governance — composition of live UW references only.
 * Phase-1: exactly one primary rule set + one executable scorecard.
 * Not wired to live underwriting routing.
 */
@Service
@RequiredArgsConstructor
public class PolicySetService {

    private final PolicySetRepository repository;
    private final CustomerCategoryRepository categoryRepository;
    private final CustomerCategoryValidator validator;
    private final AdminConfigAuditSupport auditSupport;

    @Transactional(readOnly = true)
    public List<PolicySetResponse> list() {
        Map<UUID, Long> usage = categoryUsageCounts();
        return repository.findAllByOrderByCodeAscVersionNoDesc().stream()
                .map(e -> toResponse(e, usage.getOrDefault(e.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PolicySetResponse get(UUID id) {
        PolicySetEntity e = load(id);
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID id) {
        return ConfigGovernanceHistory.historyView(load(id).getGovernanceJson());
    }

    @Transactional(readOnly = true)
    public ActivationReadinessResponse activationReadiness(UUID id) {
        PolicySetEntity e = load(id);
        List<ActivationCheck> checks = new ArrayList<>();
        boolean approved = e.getStatus() == ConfigLifecycleStatus.APPROVED;
        checks.add(new ActivationCheck(
                "STATUS_APPROVED",
                "Policy Set is APPROVED",
                approved,
                approved ? "APPROVED" : "Current status: " + e.getStatus().name()));
        checks.add(runCheck("RULE_SET_READY", "Primary rule set ACTIVE and executable",
                () -> validator.requireLiveReadyRuleSet(e.getPrimaryRuleSetId())));
        checks.add(runCheck("SINGLE_RULE_SET", "Phase-1 single rule set (no additional)",
                () -> validator.requireSingleRuleSetComposition(e.getAdditionalRuleSetIds())));
        checks.add(runCheck("SCORECARD_READY", "Scorecard ACTIVE and executable",
                () -> validator.requireExecutableScorecard(e.getScorecardId())));
        checks.add(runCheck("COMPATIBILITY", "Rule set / scorecard compatibility",
                () -> {
                    UnderwritingRuleSet rs = validator.requireLiveReadyRuleSet(e.getPrimaryRuleSetId());
                    UnderwritingScorecard sc = validator.requireExecutableScorecard(e.getScorecardId());
                    validator.assertCompatibility(rs, sc);
                }));
        checks.add(runCheck("EFFECTIVE_DATES", "Effective dates valid",
                () -> validator.validateEffectiveDates(e.getEffectiveFrom(), e.getEffectiveUntil())));
        boolean ready = checks.stream().allMatch(ActivationCheck::ok);
        return new ActivationReadinessResponse(
                e.getId(), "POLICY_SET", e.getStatus().name(), ready, checks, List.of());
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
    public PolicySetResponse createDraft(PolicySetRequest req, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        if (req == null || req.code() == null || req.code().isBlank()) {
            throw CustomerCategoryValidator.biz("code required", "POLICY_SET_CODE_REQUIRED", Map.of());
        }
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.findByCodeAndVersionNo(code, 1).isPresent()) {
            throw CustomerCategoryValidator.biz("Policy Set code already exists at version 1: " + code,
                    "POLICY_SET_CODE_EXISTS", Map.of("code", code));
        }
        List<UUID> additional = req.additionalRuleSetIds() == null
                ? List.of() : List.copyOf(req.additionalRuleSetIds());
        validator.requireSingleRuleSetComposition(additional);
        UnderwritingRuleSet rs = validator.requireLiveReadyRuleSet(req.primaryRuleSetId());
        UnderwritingScorecard sc = validator.requireLiveReadyScorecardIfPresent(req.scorecardId());
        if (sc != null) {
            validator.assertCompatibility(rs, sc);
        }
        validator.validateEffectiveDates(req.effectiveFrom(), req.effectiveUntil());

        PolicySetEntity e = PolicySetEntity.builder()
                .id(UUID.randomUUID())
                .code(code)
                .versionNo(1)
                .name(requireName(req.name()))
                .description(req.description())
                .status(ConfigLifecycleStatus.DRAFT)
                .primaryRuleSetId(req.primaryRuleSetId())
                .additionalRuleSetIds(new ArrayList<>())
                .scorecardId(req.scorecardId())
                .effectiveFrom(req.effectiveFrom())
                .effectiveUntil(req.effectiveUntil())
                .reasonForChange(req.reasonForChange())
                .governanceJson(new LinkedHashMap<>())
                .createdAt(Instant.now())
                .createdBy(actor.identity())
                .updatedBy(actor.identity())
                .build();
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "CREATED", actor, req.reasonForChange());
        repository.save(e);
        auditSupport.captureCreate("POLICY_SET", e.getId().toString(), snapshot(e), "Create DRAFT Policy Set");
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional
    public PolicySetResponse updateDraft(UUID id, PolicySetRequest req, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.DRAFT) {
            throw CustomerCategoryValidator.biz("Only DRAFT Policy Set can be edited",
                    "POLICY_SET_NOT_DRAFT", Map.of("status", e.getStatus().name()));
        }
        Map<String, Object> before = snapshot(e);
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        if (req.description() != null) {
            e.setDescription(req.description());
        }
        if (req.primaryRuleSetId() != null) {
            validator.requireLiveReadyRuleSet(req.primaryRuleSetId());
            e.setPrimaryRuleSetId(req.primaryRuleSetId());
        }
        if (req.additionalRuleSetIds() != null) {
            validator.requireSingleRuleSetComposition(req.additionalRuleSetIds());
            e.setAdditionalRuleSetIds(new ArrayList<>());
        }
        if (req.scorecardId() != null) {
            validator.requireLiveReadyScorecardIfPresent(req.scorecardId());
            e.setScorecardId(req.scorecardId());
        }
        if (req.effectiveFrom() != null || req.effectiveUntil() != null) {
            Instant from = req.effectiveFrom() != null ? req.effectiveFrom() : e.getEffectiveFrom();
            Instant until = req.effectiveUntil() != null ? req.effectiveUntil() : e.getEffectiveUntil();
            validator.validateEffectiveDates(from, until);
            if (req.effectiveFrom() != null) {
                e.setEffectiveFrom(req.effectiveFrom());
            }
            if (req.effectiveUntil() != null) {
                e.setEffectiveUntil(req.effectiveUntil());
            }
        }
        if (req.reasonForChange() != null) {
            e.setReasonForChange(req.reasonForChange());
        }
        if (e.getScorecardId() != null) {
            validator.assertCompatibility(
                    validator.requireLiveReadyRuleSet(e.getPrimaryRuleSetId()),
                    validator.requireLiveReadyScorecardIfPresent(e.getScorecardId()));
        }
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "UPDATED", actor, req.reasonForChange());
        repository.save(e);
        auditSupport.captureUpdate("POLICY_SET", e.getId().toString(), before, snapshot(e),
                "Update DRAFT Policy Set");
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional
    public PolicySetResponse submit(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.DRAFT) {
            throw CustomerCategoryValidator.biz("Only DRAFT Policy Set can be submitted",
                    "POLICY_SET_NOT_DRAFT", Map.of("status", e.getStatus().name()));
        }
        validator.requireLiveReadyRuleSet(e.getPrimaryRuleSetId());
        validator.requireSingleRuleSetComposition(e.getAdditionalRuleSetIds());
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.IN_REVIEW);
        e.setSubmittedBy(actor.identity());
        e.setSubmittedAt(Instant.now());
        e.setUpdatedBy(actor.identity());
        e.getGovernanceJson().put("submittedByUserId", actor.userId());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "SUBMITTED", actor,
                body == null ? null : body.remarks());
        repository.save(e);
        auditSupport.captureAction("POLICY_SET", e.getId().toString(), "SUBMIT", before, snapshot(e),
                "Submit Policy Set for review");
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional
    public PolicySetResponse approve(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireChecker(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.IN_REVIEW) {
            throw CustomerCategoryValidator.biz("Only IN_REVIEW Policy Set can be approved",
                    "POLICY_SET_NOT_IN_REVIEW", Map.of("status", e.getStatus().name()));
        }
        Actor submitter = new Actor(
                str(e.getGovernanceJson().get("submittedByUserId")),
                e.getSubmittedBy(),
                null);
        ConfigGovernanceRoles.forbidSelfApproval(submitter, actor);
        validator.requireLiveReadyRuleSet(e.getPrimaryRuleSetId());
        validator.requireSingleRuleSetComposition(e.getAdditionalRuleSetIds());
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.APPROVED);
        e.setApprovedBy(actor.identity());
        e.setApprovedAt(Instant.now());
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "APPROVED", actor,
                body == null ? null : body.remarks());
        repository.save(e);
        auditSupport.captureAction("POLICY_SET", e.getId().toString(), "APPROVE", before, snapshot(e),
                "Approve Policy Set");
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional
    public PolicySetResponse returnToDraft(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireChecker(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.IN_REVIEW
                && e.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz("Only IN_REVIEW or APPROVED Policy Set can be returned",
                    "POLICY_SET_NOT_RETURNABLE", Map.of("status", e.getStatus().name()));
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.DRAFT);
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "RETURNED", actor,
                body == null ? null : body.remarks());
        repository.save(e);
        auditSupport.captureAction("POLICY_SET", e.getId().toString(), "RETURN", before, snapshot(e),
                "Return Policy Set to DRAFT");
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional
    public PolicySetResponse activate(UUID id, Actor actor) {
        ConfigGovernanceRoles.requireActivator(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz(
                    "Only APPROVED Policy Set can be activated (DRAFT→ACTIVE not allowed)",
                    "POLICY_SET_NOT_APPROVED", Map.of("status", e.getStatus().name()));
        }
        UnderwritingRuleSet rs = validator.requireLiveReadyRuleSet(e.getPrimaryRuleSetId());
        validator.requireSingleRuleSetComposition(e.getAdditionalRuleSetIds());
        UnderwritingScorecard sc = validator.requireExecutableScorecard(e.getScorecardId());
        validator.assertCompatibility(rs, sc);
        validator.validateEffectiveDates(e.getEffectiveFrom(), e.getEffectiveUntil());

        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.ACTIVE);
        e.setActivatedAt(Instant.now());
        e.setActivatedBy(actor.identity());
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "ACTIVATED", actor, null);
        repository.save(e);
        auditSupport.captureAction("POLICY_SET", e.getId().toString(), "ACTIVATE", before, snapshot(e),
                "Activate Policy Set");
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional
    public PolicySetResponse retire(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireActivator(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() == ConfigLifecycleStatus.RETIRED) {
            return toResponse(e, countUsedBy(e.getId()));
        }
        if (e.getStatus() != ConfigLifecycleStatus.ACTIVE && e.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz("Only ACTIVE or APPROVED Policy Set can be retired",
                    "POLICY_SET_NOT_RETIRABLE", Map.of("status", e.getStatus().name()));
        }
        String reason = body == null ? null : (body.reason() != null ? body.reason() : body.remarks());
        if (reason == null || reason.isBlank()) {
            throw CustomerCategoryValidator.biz("retirement reason required", "RETIREMENT_REASON_REQUIRED", Map.of());
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.RETIRED);
        e.setRetiredAt(Instant.now());
        e.setRetiredBy(actor.identity());
        e.setRetirementReason(reason.trim());
        e.setUpdatedBy(actor.identity());
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "RETIRED", actor, reason);
        repository.save(e);
        auditSupport.captureAction("POLICY_SET", e.getId().toString(), "RETIRE", before, snapshot(e),
                "Retire Policy Set");
        return toResponse(e, countUsedBy(e.getId()));
    }

    @Transactional
    public PolicySetResponse copyVersion(UUID id, LifecycleActionRequest body, Actor actor) {
        ConfigGovernanceRoles.requireMaker(actor);
        PolicySetEntity src = load(id);
        if (src.getStatus() != ConfigLifecycleStatus.ACTIVE
                && src.getStatus() != ConfigLifecycleStatus.RETIRED
                && src.getStatus() != ConfigLifecycleStatus.APPROVED) {
            throw CustomerCategoryValidator.biz("Copy/version allowed from ACTIVE, APPROVED, or RETIRED",
                    "POLICY_SET_COPY_SOURCE_INVALID", Map.of("status", src.getStatus().name()));
        }
        int next = repository.findFirstByCodeOrderByVersionNoDesc(src.getCode())
                .map(p -> p.getVersionNo() + 1).orElse(1);
        PolicySetEntity e = PolicySetEntity.builder()
                .id(UUID.randomUUID())
                .code(src.getCode())
                .versionNo(next)
                .name(src.getName())
                .description(src.getDescription())
                .status(ConfigLifecycleStatus.DRAFT)
                .primaryRuleSetId(src.getPrimaryRuleSetId())
                .additionalRuleSetIds(new ArrayList<>())
                .scorecardId(src.getScorecardId())
                .seedSourceRuleSetId(src.getSeedSourceRuleSetId())
                .effectiveFrom(src.getEffectiveFrom())
                .effectiveUntil(src.getEffectiveUntil())
                .replacesPolicySetId(src.getId())
                .reasonForChange(body == null ? null : body.reason())
                .governanceJson(new LinkedHashMap<>())
                .createdAt(Instant.now())
                .createdBy(actor.identity())
                .updatedBy(actor.identity())
                .build();
        ConfigGovernanceHistory.append(e.getGovernanceJson(), "COPIED", actor,
                "Copied from " + src.getId() + " v" + src.getVersionNo());
        repository.save(e);
        auditSupport.captureCreate("POLICY_SET", e.getId().toString(), snapshot(e),
                "Copy Policy Set to new DRAFT version");
        return toResponse(e, countUsedBy(e.getId()));
    }

    PolicySetEntity load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy Set not found: " + id));
    }

    PolicySetResponse toResponse(PolicySetEntity e, int usedByCategoryCount) {
        return new PolicySetResponse(
                e.getId(),
                e.getCode(),
                e.getVersionNo(),
                e.getName(),
                e.getDescription(),
                e.getStatus().name(),
                e.getPrimaryRuleSetId(),
                e.getAdditionalRuleSetIds() == null ? List.of() : List.copyOf(e.getAdditionalRuleSetIds()),
                e.getScorecardId(),
                e.getSeedSourceRuleSetId(),
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
                e.getReplacesPolicySetId(),
                usedByCategoryCount,
                ConfigLifecycleActions.forStatus(e.getStatus()),
                ConfigGovernanceHistory.historyView(e.getGovernanceJson()));
    }

    private int countUsedBy(UUID policySetId) {
        return (int) categoryRepository.findAll().stream()
                .filter(c -> policySetId.equals(c.getPolicySetId()))
                .count();
    }

    private Map<UUID, Long> categoryUsageCounts() {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (CustomerCategoryEntity c : categoryRepository.findAll()) {
            if (c.getPolicySetId() != null) {
                counts.merge(c.getPolicySetId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    static Map<String, Object> snapshot(PolicySetEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("code", e.getCode());
        m.put("versionNo", e.getVersionNo());
        m.put("name", e.getName());
        m.put("status", e.getStatus().name());
        m.put("primaryRuleSetId", e.getPrimaryRuleSetId().toString());
        m.put("additionalRuleSetIds", e.getAdditionalRuleSetIds());
        m.put("scorecardId", e.getScorecardId() == null ? null : e.getScorecardId().toString());
        m.put("active", e.getStatus() == ConfigLifecycleStatus.ACTIVE);
        return m;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw CustomerCategoryValidator.biz("name required", "POLICY_SET_NAME_REQUIRED", Map.of());
        }
        return name.trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
