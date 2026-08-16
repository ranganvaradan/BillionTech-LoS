package com.los.core.requirement;

import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.service.underwriting.ScorecardCanonicalFactorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the shared spine {@link EvaluationContext} after W6 source acquisition.
 * Does not invent a parallel W6 fact model — loads CiUnderwritingFact values and
 * overlays acquisition outcome / sourceHints onto exact canonical IDs.
 */
@Component
@RequiredArgsConstructor
public class W6EvaluationContextFactory {

    private final CiFactSnapshotRepository snapshotRepository;
    private final CiUnderwritingFactRepository factRepository;

    public EvaluationContext build(
            UUID applicationId,
            RequirementItemEntity item,
            AcquisitionDtos.ExecutorOutcome outcome) {
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(EvaluationMode.W6_ACQUISITION)
                .applicationId(applicationId)
                .evaluationAsOf(LocalDate.now());

        Map<String, Object> facts = loadSnapshotFacts(applicationId);
        overlayOutcome(facts, outcome);
        overlayHints(facts, item);
        facts.forEach(b::fact);
        return b.build();
    }

    /** All acceptable snapshot facts as exact canonical path → unwrapped value. */
    public Map<String, Object> loadSnapshotFacts(UUID applicationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (applicationId == null) {
            return out;
        }
        Optional<CiFactSnapshot> snap =
                snapshotRepository.findTopByApplicationIdOrderBySnapshotVersionDesc(applicationId);
        if (snap.isEmpty()) {
            return out;
        }
        List<CiUnderwritingFact> rows = factRepository.findBySnapshotId(snap.get().getId());
        for (CiUnderwritingFact f : rows) {
            if (f.getCanonicalPath() == null || f.getCanonicalPath().isBlank()) {
                continue;
            }
            if (!isAcceptable(f)) {
                continue;
            }
            Object v = unwrap(f.getValue());
            if (v != null) {
                out.put(f.getCanonicalPath().trim(), v);
            }
        }
        return out;
    }

    /**
     * Resolve requirement item parameter to a GACAT canonical ID, or empty if not in catalogue
     * (legacy fixture keys remain on the boolean factReadiness path).
     */
    public static Optional<String> resolveGacatId(String parameterOrLegacyKey) {
        if (parameterOrLegacyKey == null || parameterOrLegacyKey.isBlank()) {
            return Optional.empty();
        }
        String raw = parameterOrLegacyKey.trim();
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
        // Exact catalogue identity only — do not treat alias lookups as the requirement key itself
        Optional<com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition> byId =
                registry.findById(raw);
        if (byId.isPresent() && raw.equals(byId.get().id())) {
            return Optional.of(byId.get().id());
        }
        ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(raw);
        if (bind.canonicalParameterId() != null
                && (ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                || ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus()))) {
            return Optional.of(bind.canonicalParameterId());
        }
        return Optional.empty();
    }

    /**
     * Acquisition adapters must not mark GACAT parameters ready from source success alone.
     * Legacy fixture keys may still advertise boolean readiness for W6 goldens.
     */
    public static Map<String, Boolean> acquisitionClaim(String parameterOrLegacyKey, boolean sourceMaterialPresent) {
        if (parameterOrLegacyKey == null || parameterOrLegacyKey.isBlank()) {
            return Map.of();
        }
        if (resolveGacatId(parameterOrLegacyKey).isPresent()) {
            return Map.of(parameterOrLegacyKey, false);
        }
        return Map.of(parameterOrLegacyKey, sourceMaterialPresent);
    }

    private static void overlayOutcome(Map<String, Object> facts, AcquisitionDtos.ExecutorOutcome outcome) {
        if (outcome == null || outcome.resultSummary() == null) {
            return;
        }
        Map<String, Object> s = outcome.resultSummary();
        Object score = s.get("creditScore");
        if (score == null) {
            score = s.get("bureau.score");
        }
        if (score != null && !Boolean.FALSE.equals(s.get("scorePresent"))) {
            facts.put("bureau.score", score);
        }
        Object metrics = s.get("canonicalMetrics");
        if (metrics instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String k = String.valueOf(e.getKey()).trim();
                    if (k.contains(".")) {
                        facts.put(k, e.getValue());
                    }
                }
            }
        }
        Object derived = s.get("derivedValue");
        Object target = s.get("canonicalParameterId");
        if (derived != null && target != null) {
            facts.put(String.valueOf(target), derived);
        }
    }

    @SuppressWarnings("unchecked")
    private static void overlayHints(Map<String, Object> facts, RequirementItemEntity item) {
        if (item == null || item.getSourceHints() == null) {
            return;
        }
        Map<String, Object> hints = item.getSourceHints();
        if (hints.get("inputs") instanceof Map<?, ?> inputs) {
            for (Map.Entry<?, ?> e : inputs.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String k = String.valueOf(e.getKey()).trim();
                    if (k.contains(".")) {
                        facts.put(k, e.getValue());
                    }
                }
            }
        }
        if (hints.get("extractedParameters") instanceof Map<?, ?> extracted) {
            for (Map.Entry<?, ?> e : extracted.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String k = String.valueOf(e.getKey()).trim();
                    if (k.contains(".")) {
                        facts.put(k, e.getValue());
                    }
                }
            }
        }
        Object derived = hints.get("derivedValue");
        if (derived != null && item.getCanonicalParameterId() != null
                && item.getCanonicalParameterId().contains(".")) {
            facts.put(item.getCanonicalParameterId(), derived);
        }
    }

    private static boolean isAcceptable(CiUnderwritingFact f) {
        String q = f.getQualityStatus() == null ? "" : f.getQualityStatus().toUpperCase();
        if ("FAILED".equals(q) || "REJECTED".equals(q) || "INVALID".equals(q)) {
            return false;
        }
        return !"DATA_INSUFFICIENT".equalsIgnoreCase(f.getClassification());
    }

    private static Object unwrap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.containsKey("v")) {
            return value.get("v");
        }
        if (value.size() == 1) {
            return value.values().iterator().next();
        }
        return value;
    }
}
