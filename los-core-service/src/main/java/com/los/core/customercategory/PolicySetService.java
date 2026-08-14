package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
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

@Service
@RequiredArgsConstructor
public class PolicySetService {

    private final PolicySetRepository repository;
    private final CustomerCategoryValidator validator;
    private final AdminConfigAuditSupport auditSupport;

    @Transactional(readOnly = true)
    public List<PolicySetResponse> list() {
        return repository.findAllByOrderByCodeAscVersionNoDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PolicySetResponse get(UUID id) {
        return toResponse(load(id));
    }

    @Transactional
    public PolicySetResponse createDraft(PolicySetRequest req, Actor actor) {
        requireActor(actor);
        if (req == null || req.code() == null || req.code().isBlank()) {
            throw CustomerCategoryValidator.biz("code required", "POLICY_SET_CODE_REQUIRED", Map.of());
        }
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.findByCodeAndVersionNo(code, 1).isPresent()) {
            throw CustomerCategoryValidator.biz("Policy Set code already exists at version 1: " + code,
                    "POLICY_SET_CODE_EXISTS", Map.of("code", code));
        }
        validator.requireLiveReadyRuleSet(req.primaryRuleSetId());
        List<UUID> additional = req.additionalRuleSetIds() == null
                ? List.of() : List.copyOf(req.additionalRuleSetIds());
        validator.requireLiveReadyAdditionalRuleSets(additional);
        validator.requireLiveReadyScorecardIfPresent(req.scorecardId());

        PolicySetEntity e = PolicySetEntity.builder()
                .id(UUID.randomUUID())
                .code(code)
                .versionNo(1)
                .name(requireName(req.name()))
                .description(req.description())
                .status(ConfigLifecycleStatus.DRAFT)
                .primaryRuleSetId(req.primaryRuleSetId())
                .additionalRuleSetIds(new ArrayList<>(additional))
                .scorecardId(req.scorecardId())
                .createdAt(Instant.now())
                .createdBy(actor.identity())
                .updatedBy(actor.identity())
                .build();
        repository.save(e);
        auditSupport.captureCreate("POLICY_SET", e.getId().toString(), snapshot(e), "Create DRAFT Policy Set");
        return toResponse(e);
    }

    @Transactional
    public PolicySetResponse activate(UUID id, Actor actor) {
        requireActor(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.DRAFT) {
            throw CustomerCategoryValidator.biz("Only DRAFT Policy Set can be activated",
                    "POLICY_SET_NOT_DRAFT", Map.of("status", e.getStatus().name()));
        }
        validator.requireLiveReadyRuleSet(e.getPrimaryRuleSetId());
        validator.requireLiveReadyAdditionalRuleSets(e.getAdditionalRuleSetIds());
        validator.requireLiveReadyScorecardIfPresent(e.getScorecardId());
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.ACTIVE);
        e.setActivatedAt(Instant.now());
        e.setActivatedBy(actor.identity());
        e.setUpdatedBy(actor.identity());
        repository.save(e);
        auditSupport.captureAction("POLICY_SET", e.getId().toString(), "ACTIVATE", before, snapshot(e),
                "Activate Policy Set");
        return toResponse(e);
    }

    @Transactional
    public PolicySetResponse retire(UUID id, Actor actor) {
        requireActor(actor);
        PolicySetEntity e = load(id);
        if (e.getStatus() == ConfigLifecycleStatus.RETIRED) {
            return toResponse(e);
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.RETIRED);
        e.setRetiredAt(Instant.now());
        e.setRetiredBy(actor.identity());
        e.setUpdatedBy(actor.identity());
        repository.save(e);
        auditSupport.captureAction("POLICY_SET", e.getId().toString(), "RETIRE", before, snapshot(e),
                "Retire Policy Set");
        return toResponse(e);
    }

    PolicySetEntity load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy Set not found: " + id));
    }

    PolicySetResponse toResponse(PolicySetEntity e) {
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
                e.getCreatedBy(),
                e.getUpdatedBy());
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

    private static void requireActor(Actor actor) {
        if (actor == null || actor.identity() == null || actor.identity().isBlank()) {
            throw new BusinessRuleException("Authenticated actor required", "ACTOR_REQUIRED",
                    "PROVIDE_AUTH", Map.of());
        }
    }
}
