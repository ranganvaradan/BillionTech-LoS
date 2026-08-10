package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.cutover.domain.CiCutoverCohort;
import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.service.ShadowPolicyEngine;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dual-run: legacy snapshot vs ShadowPolicyEngine + ShadowDecisionEngine.
 * Production action stays legacy.
 */
@Service
public class CutoverDualRunService {

    private final CutoverStore store;
    private final CutoverComparisonClassifier classifier;
    private final CreditIntelligenceProperties properties;
    private final ShadowPolicyEngine policyEngine;
    private final ShadowDecisionEngine decisionEngine;
    private final CutoverObservability observability;
    private final ContentHasher hasher;

    public CutoverDualRunService(
            CutoverStore store,
            CutoverComparisonClassifier classifier,
            CreditIntelligenceProperties properties) {
        this(store, classifier, properties, new ShadowPolicyEngine(), new ShadowDecisionEngine(),
                new CutoverObservability(), new ContentHasher());
    }

    @Autowired
    public CutoverDualRunService(
            CutoverStore store,
            CutoverComparisonClassifier classifier,
            CreditIntelligenceProperties properties,
            ShadowPolicyEngine policyEngine,
            ShadowDecisionEngine decisionEngine,
            CutoverObservability observability,
            ContentHasher hasher) {
        this.store = store;
        this.classifier = classifier;
        this.properties = properties;
        this.policyEngine = policyEngine != null ? policyEngine : new ShadowPolicyEngine();
        this.decisionEngine = decisionEngine != null ? decisionEngine : new ShadowDecisionEngine();
        this.observability = observability != null ? observability : new CutoverObservability();
        this.hasher = hasher != null ? hasher : new ContentHasher();
    }

    /**
     * Run dual comparison from provided legacy + canonical outcome maps (fixture-friendly).
     * Does not switch production authority.
     */
    public CiCutoverComparison compareSnapshots(
            UUID cohortId,
            UUID applicationId,
            UUID evaluationContextId,
            Map<String, Object> legacySnapshot,
            Map<String, Object> canonicalSnapshot,
            boolean legacyUsedDefault,
            Map<String, Object> decisionTrace) {
        assertDualRunEnabled();
        CiCutoverCohort cohort = store.findCohort(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cohort not found"));

        CutoverComparisonClassifier.ClassificationResult cls =
                classifier.classify(legacySnapshot, canonicalSnapshot, legacyUsedDefault);

        Map<String, Object> L = legacySnapshot == null ? Map.of() : legacySnapshot;
        Map<String, Object> C = canonicalSnapshot == null ? Map.of() : canonicalSnapshot;

        Map<String, Object> trace = new LinkedHashMap<>();
        if (decisionTrace != null) trace.putAll(decisionTrace);
        trace.put("classificationDetail", cls.detail());
        trace.put("productionAuthority", "LEGACY");
        trace.put("shadowOnly", true);
        trace.put("contentHash", hasher.hashMap(Map.of("legacy", L, "canonical", C)));

        CiCutoverComparison row = CiCutoverComparison.builder()
                .cohortId(cohort.getId())
                .applicationId(applicationId)
                .evaluationContextId(evaluationContextId)
                .legacyPolicyOutcome(str(L.get("policyOutcome"), str(L.get("outcome"), null)))
                .canonicalPolicyOutcome(str(C.get("policyOutcome"), str(C.get("outcome"), null)))
                .legacyAmount(num(L.get("amount")))
                .canonicalAmount(num(C.get("amount")))
                .legacyTenure(intVal(L.get("tenure")))
                .canonicalTenure(intVal(C.get("tenure")))
                .legacyPricing(num(L.get("pricing")))
                .canonicalPricing(num(C.get("pricing")))
                .legacyAuthority(str(L.get("authority"), null))
                .canonicalAuthority(str(C.get("authority"), null))
                .legacyConditions(listOf(L.get("conditions")))
                .canonicalConditions(listOf(C.get("conditions")))
                .comparisonClass(cls.comparisonClass().name())
                .materiality(cls.materiality())
                .rootCause(cls.rootCause() == null ? null : cls.rootCause().name())
                .reasonCodes(buildReasons(cls))
                .decisionTrace(trace)
                .reviewStatus("PENDING")
                .createdAt(Instant.now())
                .build();

        observability.inc("dual_run_comparisons");
        if (cls.comparisonClass() == ComparisonClass.EXACT_MATCH) {
            observability.inc("dual_run_exact_match");
        } else {
            observability.inc("dual_run_mismatch");
        }
        return store.saveComparison(row);
    }

    /**
     * Optional engine-backed dual-run when package + strategy are supplied.
     */
    public CiCutoverComparison runWithEngines(
            UUID cohortId,
            UUID applicationId,
            UUID evaluationContextId,
            Map<String, Object> legacySnapshot,
            boolean legacyUsedDefault,
            CiExecutablePolicyPackage policyPackage,
            PolicyEvaluationInput policyInput,
            CiDecisionStrategy strategy,
            DecisionRuntimeInput decisionInput) {
        assertDualRunEnabled();
        Map<String, Object> canonical = new LinkedHashMap<>();
        Map<String, Object> trace = new LinkedHashMap<>();
        try {
            if (policyPackage != null && policyInput != null) {
                CiPolicyEvaluation eval = policyEngine.evaluate(policyPackage, policyInput);
                canonical.put("policyOutcome", eval.getOverallOutcome());
                canonical.put("outcome", eval.getOverallOutcome());
                trace.put("policyEvaluationId", eval.getId());
                trace.put("policyHash", eval.getDeterministicHash());
            }
            if (strategy != null && decisionInput != null) {
                CiCreditRecommendation rec = decisionEngine.recommend(strategy, decisionInput);
                canonical.put("amount", rec.getRecommendedAmount());
                canonical.put("tenure", rec.getRecommendedTenureMonths());
                canonical.put("pricing", rec.getRecommendedFinalRate());
                canonical.put("authority", rec.getApprovalAuthorityLevel());
                canonical.put("conditions", rec.getConditionsPrecedent());
                if (!canonical.containsKey("outcome")) {
                    canonical.put("outcome", rec.getRecommendationOutcome());
                    canonical.put("policyOutcome", rec.getRecommendationOutcome());
                }
                trace.put("recommendationId", rec.getId());
                trace.put("decisionHash", rec.getDeterministicDecisionHash());
                trace.put("authoritative", false);
            }
        } catch (Exception ex) {
            observability.inc("canonical_evaluation_failures");
            canonical.put("outcome", "DATA_INSUFFICIENT");
            canonical.put("policyOutcome", "DATA_INSUFFICIENT");
            trace.put("engineError", ex.getClass().getSimpleName());
        }
        return compareSnapshots(
                cohortId, applicationId, evaluationContextId,
                legacySnapshot, canonical, legacyUsedDefault, trace);
    }

    public Map<String, Object> statistics(UUID cohortId) {
        List<CiCutoverComparison> rows = store.listComparisons(cohortId);
        int n = rows.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationsDualRun", n);
        out.put("dataOriginNote",
                "Computed only from persisted dual-run rows (fixture/dev). Not fabricated.");
        if (n == 0) {
            out.put("exactMatchPct", null);
            out.put("nonMaterialDifferencePct", null);
            out.put("materialDifferencePct", null);
            out.put("canonicalStricterPct", null);
            out.put("canonicalMorePermissivePct", null);
            out.put("canonicalDiPct", null);
            out.put("legacyDefaultDependentPct", null);
            out.put("insufficientSample", true);
            return out;
        }
        out.put("exactMatchPct", pct(rows, ComparisonClass.EXACT_MATCH));
        out.put("nonMaterialDifferencePct", pct(rows, ComparisonClass.NON_MATERIAL_DIFFERENCE));
        out.put("materialDifferencePct", materialPct(rows));
        out.put("canonicalStricterPct", pct(rows, ComparisonClass.CANONICAL_STRICTER));
        out.put("canonicalMorePermissivePct", pct(rows, ComparisonClass.CANONICAL_MORE_PERMISSIVE));
        out.put("canonicalDiPct", pct(rows, ComparisonClass.CANONICAL_DATA_INSUFFICIENT));
        out.put("legacyDefaultDependentPct", pct(rows, ComparisonClass.LEGACY_DEFAULT_DEPENDENT));
        out.put("insufficientSample", n < 5);
        Map<String, Long> byClass = new LinkedHashMap<>();
        for (CiCutoverComparison r : rows) {
            byClass.merge(r.getComparisonClass(), 1L, Long::sum);
        }
        out.put("byClass", byClass);
        return out;
    }

    private void assertDualRunEnabled() {
        CreditIntelligenceProperties.Cutover c = properties.getCutover();
        if (c == null || !c.isEnabled() || !c.isDualRunEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cutover dual-run disabled (feature flags default false)");
        }
    }

    private static BigDecimal pct(List<CiCutoverComparison> rows, ComparisonClass cls) {
        long hit = rows.stream().filter(r -> cls.name().equals(r.getComparisonClass())).count();
        return BigDecimal.valueOf(hit * 100.0 / rows.size()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal materialPct(List<CiCutoverComparison> rows) {
        long hit = rows.stream().filter(r -> "MATERIAL".equals(r.getMateriality())).count();
        return BigDecimal.valueOf(hit * 100.0 / rows.size()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static List<String> buildReasons(CutoverComparisonClassifier.ClassificationResult cls) {
        List<String> reasons = new ArrayList<>();
        reasons.add(cls.comparisonClass().name());
        if (cls.rootCause() != null) reasons.add(cls.rootCause().name());
        return reasons;
    }

    private static String str(Object v, String d) {
        return v == null ? d : String.valueOf(v);
    }

    private static BigDecimal num(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> l) return new ArrayList<>((List<Object>) l);
        return List.of(v);
    }
}
