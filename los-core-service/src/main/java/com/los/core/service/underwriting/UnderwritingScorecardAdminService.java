package com.los.core.service.underwriting;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.UnderwritingScorecardRequest;
import com.los.core.model.dto.response.UnderwritingScorecardResponse;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnderwritingScorecardAdminService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";

    private final UnderwritingScorecardRepository repository;
    private final AdminConfigAuditSupport adminConfigAuditSupport;

    public List<UnderwritingScorecardResponse> list() {
        return repository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public UnderwritingScorecardResponse create(UnderwritingScorecardRequest r) {
        UnderwritingScorecard e = new UnderwritingScorecard();
        apply(e, r);
        e.setLineageId(null); // assigned after first save if needed
        e.setParentScorecardId(null);
        e.setSafetyJson(e.getSafetyJson() == null ? Map.of() : e.getSafetyJson());
        if (r.isActive()) {
            ScorecardSafetyValidator.ValidationResult activation =
                    ScorecardSafetyValidator.validateForActivation(e);
            if (!activation.ok()) {
                throw new BusinessRuleException(
                        "Scorecard cannot become ACTIVE: " + String.join("; ", activation.problems()),
                        "SCORECARD_ACTIVATION_BLOCKED",
                        "CONFIRM_MISSING_DATA_POLICIES",
                        Map.of("problems", activation.problems()));
            }
            activate(e);
        } else {
            e.setActive(false);
            e.setStatus(STATUS_DRAFT);
            e.setActivatedAt(null);
        }
        validateBandsOrThrow(e);
        e.setUpdatedAt(Instant.now());
        UnderwritingScorecard saved = repository.save(e);
        if (saved.getLineageId() == null) {
            saved.setLineageId(saved.getId());
            saved = repository.save(saved);
        }
        UnderwritingScorecardResponse resp = toResponse(saved);
        adminConfigAuditSupport.captureCreate("UNDERWRITING_SCORECARD", resp.getId().toString(), resp, "Underwriting scorecard created");
        return resp;
    }

    @Transactional
    public UnderwritingScorecardResponse update(UUID id, UnderwritingScorecardRequest r) {
        UnderwritingScorecard e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
        UnderwritingScorecardResponse before = toResponse(e);

        boolean wasActive = e.isExecutionActive();
        if (wasActive && executionAffectingChange(e, r)) {
            throw new BusinessRuleException(
                    "ACTIVE scorecard is immutable for execution-affecting fields. Create a new version (DRAFT) instead.",
                    "SCORECARD_ACTIVE_IMMUTABLE",
                    "CREATE_NEW_VERSION",
                    Map.of("scorecardId", id.toString(), "status", e.getStatus(), "version", e.getVersion()));
        }

        if (!wasActive) {
            apply(e, r);
            validateBandsOrThrow(e);
        } else {
            // non-execution metadata only (name trim already same if blocked above)
            if (r.getName() != null && !r.getName().isBlank()) {
                e.setName(r.getName().trim());
            }
        }

        if (r.isActive() && !e.isActive()) {
            ScorecardSafetyValidator.ValidationResult activation =
                    ScorecardSafetyValidator.validateForActivation(e);
            if (!activation.ok()) {
                throw new BusinessRuleException(
                        "Scorecard cannot become ACTIVE: " + String.join("; ", activation.problems()),
                        "SCORECARD_ACTIVATION_BLOCKED",
                        "CONFIRM_MISSING_DATA_POLICIES",
                        Map.of(
                                "problems", activation.problems(),
                                "missingPoliciesExplicit", activation.missingPoliciesExplicit(),
                                "missingPoliciesConfirmed", activation.missingPoliciesConfirmed()));
            }
            activate(e);
            validateBandsOrThrow(e);
        } else if (!r.isActive() && e.isActive()) {
            retire(e);
        }

        e.setUpdatedAt(Instant.now());
        UnderwritingScorecardResponse after = toResponse(repository.save(e));
        adminConfigAuditSupport.captureUpdate("UNDERWRITING_SCORECARD", id.toString(), before, after, "Underwriting scorecard updated");
        return after;
    }

    /**
     * ACTIVE vN → clone DRAFT vN+1 (editable). Original ACTIVE remains immutable and active until
     * the draft is explicitly activated (which retires peers in the same lineage optionally).
     */
    @Transactional
    public UnderwritingScorecardResponse createNewVersion(UUID id) {
        UnderwritingScorecard source = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
        UUID lineage = source.getLineageId() != null ? source.getLineageId() : source.getId();
        int nextVersion = repository.findAll().stream()
                .filter(s -> lineage.equals(s.getLineageId() != null ? s.getLineageId() : s.getId()))
                .mapToInt(UnderwritingScorecard::getVersion)
                .max()
                .orElse(source.getVersion()) + 1;

        UnderwritingScorecard draft = UnderwritingScorecard.builder()
                .name(source.getName())
                .borrowerType(source.getBorrowerType())
                .loanProduct(source.getLoanProduct())
                .version(nextVersion)
                .priority(source.getPriority())
                .minAmount(source.getMinAmount())
                .maxAmount(source.getMaxAmount())
                .geography(copyMap(source.getGeography()))
                .scorecardJson(copyMap(source.getScorecardJson()))
                .thresholdsJson(copyMap(source.getThresholdsJson()))
                .hardRulesJson(copyMap(source.getHardRulesJson()))
                // Inherit recommended missing-data classifications; confirmation required before activate
                .safetyJson(ScorecardSafetyValidator.inheritedSafetyForNewVersion(source))
                .active(false)
                .status(STATUS_DRAFT)
                .lineageId(lineage)
                .parentScorecardId(source.getId())
                .activatedAt(null)
                .build();
        validateBandsOrThrow(draft);
        draft.setUpdatedAt(Instant.now());
        UnderwritingScorecardResponse saved = toResponse(repository.save(draft));
        adminConfigAuditSupport.captureCreate(
                "UNDERWRITING_SCORECARD",
                saved.getId().toString(),
                saved,
                "Scorecard draft version created from " + id);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        UnderwritingScorecard e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
        if (e.isExecutionActive()) {
            throw new BusinessRuleException(
                    "Cannot delete an ACTIVE scorecard. Create a new version or retire it first.",
                    "SCORECARD_ACTIVE_DELETE_FORBIDDEN",
                    "CREATE_NEW_VERSION_OR_RETIRE",
                    null);
        }
        UnderwritingScorecardResponse before = toResponse(e);
        repository.delete(e);
        adminConfigAuditSupport.captureDelete("UNDERWRITING_SCORECARD", id.toString(), before, "Underwriting scorecard deleted");
    }

    private void activate(UnderwritingScorecard e) {
        e.setActive(true);
        e.setStatus(STATUS_ACTIVE);
        e.setActivatedAt(Instant.now());
        if (e.getLineageId() == null && e.getId() != null) {
            e.setLineageId(e.getId());
        }
    }

    private void retire(UnderwritingScorecard e) {
        e.setActive(false);
        e.setStatus(STATUS_RETIRED);
    }

    private void apply(UnderwritingScorecard e, UnderwritingScorecardRequest r) {
        e.setName(r.getName().trim());
        e.setBorrowerType(r.getBorrowerType().name());
        e.setLoanProduct(r.getLoanProduct().trim());
        e.setVersion(Math.max(1, r.getVersion()));
        e.setPriority(r.getPriority());
        e.setMinAmount(r.getMinAmount());
        e.setMaxAmount(r.getMaxAmount());
        e.setGeography(r.getGeography());
        e.setScorecardJson(safeMap(r.getScorecardJson()));
        e.setThresholdsJson(safeMap(r.getThresholdsJson()));
        e.setHardRulesJson(safeMap(r.getHardRulesJson()));
        Map<String, Object> safety = new LinkedHashMap<>();
        if (e.getSafetyJson() != null) {
            safety.putAll(e.getSafetyJson());
        }
        if (r.getSafetyJson() != null) {
            safety.putAll(r.getSafetyJson());
        }
        safety.putIfAbsent("weightSemantics", "METADATA_ONLY_NOT_USED_IN_FORMULA");
        safety.putIfAbsent("bandSemantics", ScorecardExclusiveBandModel.MODE_EXCLUSIVE);
        safety.putIfAbsent("denominatorSemantics", "SUM_OF_FACTOR_MAX_WHERE_FACTOR_MAX_IS_MAX_BAND_POINTS");
        // Recompute explicit flag from factorPolicies
        ScorecardSafetyValidator.ValidationResult vr = ScorecardSafetyValidator.validate(
                UnderwritingScorecard.builder()
                        .scorecardJson(e.getScorecardJson())
                        .thresholdsJson(e.getThresholdsJson())
                        .hardRulesJson(e.getHardRulesJson())
                        .safetyJson(safety)
                        .build());
        safety.put("missingDataPoliciesExplicit", vr.missingPoliciesExplicit());
        e.setSafetyJson(safety);
    }

    /**
     * Confirm missing-data classifications on a DRAFT after human review.
     * Sets {@code missingDataPoliciesConfirmed=true} only when every factor is explicit.
     */
    @Transactional
    public UnderwritingScorecardResponse confirmMissingDataPolicies(UUID id) {
        UnderwritingScorecard e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
        if (e.isExecutionActive()) {
            throw new BusinessRuleException(
                    "ACTIVE scorecard missing-data policies are immutable; create a new version.",
                    "SCORECARD_ACTIVE_IMMUTABLE",
                    "CREATE_NEW_VERSION",
                    null);
        }
        ScorecardSafetyValidator.ValidationResult vr = ScorecardSafetyValidator.validate(e);
        if (!vr.missingPoliciesExplicit()) {
            throw new BusinessRuleException(
                    "Cannot confirm: one or more factors lack explicit missing-data policy",
                    "SCORECARD_MISSING_POLICY_INCOMPLETE",
                    "SET_FACTOR_POLICIES",
                    Map.of("factors", vr.factors()));
        }
        Map<String, Object> safety = new LinkedHashMap<>(e.getSafetyJson() == null ? Map.of() : e.getSafetyJson());
        safety.put("missingDataPoliciesExplicit", true);
        safety.put("missingDataPoliciesConfirmed", true);
        e.setSafetyJson(safety);
        e.setUpdatedAt(Instant.now());
        return toResponse(repository.save(e));
    }

    private boolean executionAffectingChange(UnderwritingScorecard e, UnderwritingScorecardRequest r) {
        if (!Objects.equals(e.getBorrowerType(), r.getBorrowerType() == null ? null : r.getBorrowerType().name())) {
            return true;
        }
        if (!Objects.equals(e.getLoanProduct(), r.getLoanProduct() == null ? null : r.getLoanProduct().trim())) {
            return true;
        }
        if (e.getPriority() != r.getPriority()) {
            return true;
        }
        if (e.getVersion() != Math.max(1, r.getVersion())) {
            return true;
        }
        if (!Objects.equals(e.getMinAmount(), r.getMinAmount()) || !Objects.equals(e.getMaxAmount(), r.getMaxAmount())) {
            return true;
        }
        if (!Objects.equals(safeMap(e.getGeography()), safeMap(r.getGeography()))) {
            return true;
        }
        if (!Objects.equals(safeMap(e.getScorecardJson()), safeMap(r.getScorecardJson()))) {
            return true;
        }
        if (!Objects.equals(safeMap(e.getThresholdsJson()), safeMap(r.getThresholdsJson()))) {
            return true;
        }
        if (!Objects.equals(safeMap(e.getHardRulesJson()), safeMap(r.getHardRulesJson()))) {
            return true;
        }
        // Name-only change is allowed for ACTIVE
        return false;
    }

    @SuppressWarnings("unchecked")
    private void validateBandsOrThrow(UnderwritingScorecard e) {
        Map<String, Object> scj = e.getScorecardJson() != null ? e.getScorecardJson() : Map.of();
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        Object rows = scj.get("rows");
        if (rows instanceof List<?> rlist) {
            for (Object o : rlist) {
                if (o instanceof Map) {
                    rowMaps.add((Map<String, Object>) o);
                }
            }
        }
        ScorecardExclusiveBandModel.Model model = ScorecardExclusiveBandModel.fromRows(rowMaps);
        Map<String, Object> safety = new LinkedHashMap<>(e.getSafetyJson() == null ? Map.of() : e.getSafetyJson());
        safety.put("bandValidationSafe", model.safe());
        safety.put("bandValidationProblems", model.problems());
        safety.put("weightSemantics", "METADATA_ONLY_NOT_USED_IN_FORMULA");
        e.setSafetyJson(safety);
        if (!model.safe() && e.isActive()) {
            throw new BusinessRuleException(
                    "Scorecard band configuration is ambiguous/unsafe: " + String.join("; ", model.problems()),
                    "SCORECARD_BANDS_UNSAFE",
                    "FIX_BANDS",
                    Map.of("problems", model.problems()));
        }
    }

    private static Map<String, Object> safeMap(Map<String, Object> m) {
        return m != null ? m : Map.of();
    }

    private static Map<String, Object> copyMap(Map<String, Object> m) {
        return m == null ? Map.of() : new LinkedHashMap<>(m);
    }

    private UnderwritingScorecardResponse toResponse(UnderwritingScorecard e) {
        return UnderwritingScorecardResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .borrowerType(e.getBorrowerType())
                .loanProduct(e.getLoanProduct())
                .version(e.getVersion())
                .priority(e.getPriority())
                .minAmount(e.getMinAmount())
                .maxAmount(e.getMaxAmount())
                .geography(e.getGeography())
                .scorecardJson(e.getScorecardJson())
                .thresholdsJson(e.getThresholdsJson())
                .hardRulesJson(e.getHardRulesJson())
                .active(e.isActive())
                .status(e.getStatus())
                .lineageId(e.getLineageId())
                .parentScorecardId(e.getParentScorecardId())
                .activatedAt(e.getActivatedAt())
                .safetyJson(e.getSafetyJson())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
