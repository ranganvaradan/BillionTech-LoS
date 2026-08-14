package com.los.core.customercategory;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.CategoryCriteria;
import com.los.core.customercategory.CustomerCategoryOverlapDetector.OverlapWarning;
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
public class CustomerCategoryService {

    private final CustomerCategoryRepository repository;
    private final PolicySetRepository policySetRepository;
    private final CustomerCategoryValidator validator;
    private final AdminConfigAuditSupport auditSupport;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        List<CustomerCategoryEntity> all = repository.findAllByOrderByCodeAscVersionNoDesc();
        List<OverlapWarning> overlaps = detectOverlapsAmong(all.stream()
                .filter(c -> c.getStatus() == ConfigLifecycleStatus.ACTIVE
                        || c.getStatus() == ConfigLifecycleStatus.DRAFT)
                .toList());
        return all.stream().map(c -> toResponse(c, overlaps)).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        CustomerCategoryEntity e = load(id);
        List<OverlapWarning> overlaps = detectOverlapsAmong(repository.findAll().stream()
                .filter(c -> c.getStatus() == ConfigLifecycleStatus.ACTIVE
                        || c.getStatus() == ConfigLifecycleStatus.DRAFT)
                .toList());
        return toResponse(e, overlaps);
    }

    @Transactional
    public CategoryResponse createDraft(CategoryRequest req, Actor actor) {
        requireActor(actor);
        if (req == null || req.code() == null || req.code().isBlank()) {
            throw CustomerCategoryValidator.biz("code required", "CATEGORY_CODE_REQUIRED", Map.of());
        }
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.findByCodeAndVersionNo(code, 1).isPresent()) {
            throw CustomerCategoryValidator.biz("Category code already exists at version 1: " + code,
                    "CATEGORY_CODE_EXISTS", Map.of("code", code));
        }
        String borrower = validator.normalizeBorrowerType(req.borrowerType());
        String product = validator.normalizeLoanProduct(req.loanProduct());
        String intake = validator.normalizeIntakeSegment(req.intakeSegment());
        validator.validateAmountRange(req.minAmount(), req.maxAmount());
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
                .reviewStatus("READY")
                .inferenceNotes(new LinkedHashMap<>())
                .createdAt(Instant.now())
                .createdBy(actor.identity())
                .updatedBy(actor.identity())
                .build();
        repository.save(e);
        auditSupport.captureCreate("CUSTOMER_CATEGORY", e.getId().toString(), snapshot(e),
                "Create DRAFT Customer Category");
        return toResponse(e, detectOverlapsAmong(List.of(e)));
    }

    /**
     * ACTIVE matching criteria cannot be mutated in place.
     * DRAFT may update matching fields; ACTIVE may only update name/description (non-matching).
     */
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest req, Actor actor) {
        requireActor(actor);
        CustomerCategoryEntity e = load(id);
        Map<String, Object> before = snapshot(e);
        if (e.getStatus() == ConfigLifecycleStatus.RETIRED) {
            throw CustomerCategoryValidator.biz("RETIRED category cannot be edited",
                    "CATEGORY_RETIRED", Map.of("id", id.toString()));
        }
        if (e.getStatus() == ConfigLifecycleStatus.ACTIVE) {
            boolean matchingChanged = matchingChanged(e, req);
            if (matchingChanged) {
                throw CustomerCategoryValidator.biz(
                        "ACTIVE category matching criteria cannot be mutated in place; create a new version",
                        "ACTIVE_CATEGORY_IMMUTABLE",
                        Map.of("id", id.toString(), "code", e.getCode()));
            }
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
        // DRAFT — full update allowed
        e.setName(requireName(req.name() != null ? req.name() : e.getName()));
        if (req.description() != null) {
            e.setDescription(req.description());
        }
        if (req.borrowerType() != null) {
            e.setBorrowerType(validator.normalizeBorrowerType(req.borrowerType()));
        }
        if (req.loanProduct() != null) {
            e.setLoanProduct(validator.normalizeLoanProduct(req.loanProduct()));
        }
        if (req.intakeSegment() != null) {
            e.setIntakeSegment(validator.normalizeIntakeSegment(req.intakeSegment()));
        }
        if (req.minAmount() != null || req.maxAmount() != null
                || (req.minAmount() == null && req.maxAmount() == null && matchingAmountClear(req))) {
            // only update amounts when request explicitly carries them via dedicated path —
            // for simplicity Step 1: always set from request when provided fields present
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
        e.setUpdatedBy(actor.identity());
        repository.save(e);
        auditSupport.captureUpdate("CUSTOMER_CATEGORY", e.getId().toString(), before, snapshot(e),
                "Update DRAFT Customer Category");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse activate(UUID id, Actor actor) {
        requireActor(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() != ConfigLifecycleStatus.DRAFT) {
            throw CustomerCategoryValidator.biz("Only DRAFT category can be activated",
                    "CATEGORY_NOT_DRAFT", Map.of("status", e.getStatus().name()));
        }
        PolicySetEntity ps = policySetRepository.findById(e.getPolicySetId())
                .orElseThrow(() -> CustomerCategoryValidator.biz("Policy Set missing",
                        "POLICY_SET_NOT_FOUND", Map.of()));
        if (ps.getStatus() != ConfigLifecycleStatus.ACTIVE) {
            throw CustomerCategoryValidator.biz("Policy Set must be ACTIVE before category activation",
                    "POLICY_SET_NOT_READY", Map.of("policySetStatus", ps.getStatus().name()));
        }
        validator.validateMatchDimensions(
                e.getBorrowerType(), e.getLoanProduct(), e.getIntakeSegment(),
                e.getMinAmount(), e.getMaxAmount());
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.ACTIVE);
        e.setActivatedAt(Instant.now());
        e.setActivatedBy(actor.identity());
        e.setUpdatedBy(actor.identity());
        repository.save(e);
        auditSupport.captureAction("CUSTOMER_CATEGORY", e.getId().toString(), "ACTIVATE",
                before, snapshot(e), "Activate Customer Category (overlaps are WARNING only)");
        return get(e.getId());
    }

    @Transactional
    public CategoryResponse retire(UUID id, Actor actor) {
        requireActor(actor);
        CustomerCategoryEntity e = load(id);
        if (e.getStatus() == ConfigLifecycleStatus.RETIRED) {
            return get(id);
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(ConfigLifecycleStatus.RETIRED);
        e.setRetiredAt(Instant.now());
        e.setRetiredBy(actor.identity());
        e.setUpdatedBy(actor.identity());
        repository.save(e);
        auditSupport.captureAction("CUSTOMER_CATEGORY", e.getId().toString(), "RETIRE",
                before, snapshot(e), "Retire Customer Category");
        return get(e.getId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> overlapReport() {
        List<CustomerCategoryEntity> relevant = repository.findAll().stream()
                .filter(c -> c.getStatus() == ConfigLifecycleStatus.ACTIVE
                        || c.getStatus() == ConfigLifecycleStatus.DRAFT)
                .toList();
        return detectOverlapsAmong(relevant).stream().map(this::overlapToMap).toList();
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
                e.getCreatedBy(),
                e.getUpdatedBy(),
                mine);
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
        if (req.borrowerType() != null && !req.borrowerType().equalsIgnoreCase(e.getBorrowerType())) {
            return true;
        }
        if (req.loanProduct() != null && !req.loanProduct().equals(e.getLoanProduct())
                && !(MatchWildcard.isAny(req.loanProduct()) && MatchWildcard.isAny(e.getLoanProduct()))) {
            return true;
        }
        if (req.intakeSegment() != null && !req.intakeSegment().equalsIgnoreCase(e.getIntakeSegment())) {
            return true;
        }
        if (req.policySetId() != null && !req.policySetId().equals(e.getPolicySetId())) {
            return true;
        }
        if (req.minAmount() != null || req.maxAmount() != null) {
            if (req.minAmount() != null
                    && (e.getMinAmount() == null || req.minAmount().compareTo(e.getMinAmount()) != 0)) {
                return true;
            }
            if (req.maxAmount() != null
                    && (e.getMaxAmount() == null || req.maxAmount().compareTo(e.getMaxAmount()) != 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchingAmountClear(CategoryRequest req) {
        return false;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw CustomerCategoryValidator.biz("name required", "CATEGORY_NAME_REQUIRED", Map.of());
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
