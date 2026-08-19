package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationEntity;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationRepository;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalResolutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.canonicallive.CanonicalLiveUnderwritingService;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * W11.3 canonical shadow underwriting. Distinct from {@code ShadowCreditEvaluationService}.
 * Never writes loan_applications.credit_decision or application status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanonicalShadowUnderwritingService {

    public static final String SERVICE = "CanonicalShadowUnderwritingService";
    public static final String AGGREGATION_AUTHORITY =
            "PolicyScorecardPrecedence: policy FAIL blocks APPROVE; policy DI/REFER blocks silent score APPROVE; "
                    + "policy PASS defers to canonical scorecard band; no CreditDecisionServiceImpl.";

    private final CreditIntelligenceProperties properties;
    private final CanonicalApplicationConfigurationRepository freezeRepository;
    private final CanonicalShadowEvaluationRepository evaluationRepository;
    private final CanonicalShadowComparisonRepository comparisonRepository;
    private final CanonicalObservationalEvaluationService observationalEvaluation;

    public boolean isEnabledFor(LoanApplication app) {
        CreditIntelligenceProperties.CanonicalShadow cfg = properties.getCanonicalShadow();
        if (cfg == null || cfg.getMode() != CanonicalShadowMode.LEGACY_WITH_CANONICAL_SHADOW) {
            return false;
        }
        if (app == null) {
            return false;
        }
        if (notEmpty(cfg.getTenantIds())) {
            String tid = properties.getDefaultTenantId() == null ? "" : properties.getDefaultTenantId().toString();
            if (cfg.getTenantIds().stream().noneMatch(t -> tid.equalsIgnoreCase(t))) {
                return false;
            }
        }
        if (notEmpty(cfg.getProductCodes())) {
            String product = app.getLoanProduct() == null ? "" : app.getLoanProduct();
            if (cfg.getProductCodes().stream().noneMatch(p -> product.equalsIgnoreCase(p))) {
                return false;
            }
        }
        if (notEmpty(cfg.getCustomerCategoryIds())) {
            UUID cat = app.getSelectedCustomerCategoryId();
            if (cat == null || cfg.getCustomerCategoryIds().stream()
                    .noneMatch(id -> cat.toString().equalsIgnoreCase(id))) {
                return false;
            }
        }
        return true;
    }

    /**
     * After live underwriting. Never throws. Never mutates live decision.
     */
    public void afterLiveDecision(
            LoanApplication app,
            UnderwritingEvaluation productionEval,
            MultiRuleEvalResult multi,
            String productionOutcome) {
        if (isCanonicalLiveProduction(productionEval)) {
            log.debug("canonical shadow skipped — live authority already CANONICAL_LIVE app={}",
                    app == null ? null : app.getId());
            return;
        }
        try {
            runAndPersist(app, productionEval, multi, productionOutcome, false);
        } catch (Exception ex) {
            log.warn("Canonical shadow afterLiveDecision failed for {}: {}",
                    app == null ? null : app.getId(), ex.getMessage());
            try {
                persistError(app, productionEval, List.of(CanonicalShadowFailureCode.CANONICAL_RUNTIME_ERROR.name()),
                        ex.getMessage());
            } catch (Exception persistEx) {
                log.warn("Canonical shadow error persist failed: {}", persistEx.getMessage());
            }
        }
    }

    /**
     * Controlled Internal/fixture run. Does not underwrite or change customer outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CanonicalShadowEvaluationEntity runControlled(LoanApplication app) {
        return runAndPersist(app, null, null, app == null ? null : app.getCreditDecision(), true);
    }

    CanonicalShadowEvaluationEntity runAndPersist(
            LoanApplication app,
            UnderwritingEvaluation productionEval,
            MultiRuleEvalResult multi,
            String productionOutcome,
            boolean controlled) {
        if (app == null || app.getId() == null) {
            return null;
        }
        if (!controlled && !isEnabledFor(app)) {
            return null;
        }
        if (controlled && !isEnabledFor(app)
                && properties.getCanonicalShadow().getMode() != CanonicalShadowMode.LEGACY_WITH_CANONICAL_SHADOW) {
            return persistTerminal(app, productionEval, null, "unresolved",
                    "NOT_ELIGIBLE", CanonicalShadowDecision.NOT_EXECUTABLE,
                    List.of(CanonicalShadowFailureCode.SHADOW_NOT_ENABLED.name()),
                    List.of(), List.of(), Map.of(), Map.of(), Map.of(),
                    captureLegacy(productionEval, multi, productionOutcome));
        }

        Optional<CanonicalApplicationConfigurationEntity> freezeRow =
                freezeRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(
                        app.getId(), CanonicalResolutionStatus.RESOLVED.name());
        if (freezeRow.isEmpty()) {
            return persistTerminal(app, productionEval, null, "unresolved",
                    "NOT_ELIGIBLE", CanonicalShadowDecision.NOT_EXECUTABLE,
                    List.of(CanonicalShadowFailureCode.FROZEN_PACKAGE_NOT_RESOLVED.name()),
                    List.of(), List.of(), Map.of(), Map.of(), Map.of(),
                    captureLegacy(productionEval, multi, productionOutcome));
        }
        String hash = freezeRow.get().getIdentityHash();
        Optional<CanonicalShadowEvaluationEntity> existing = existing(productionEval, app.getId(), hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        CanonicalObservationalEvaluation ev = observationalEvaluation.evaluateFreeze(freezeRow.get());
        CanonicalShadowDecision decision;
        try {
            decision = CanonicalShadowDecision.valueOf(ev.canonicalDecision() == null
                    ? CanonicalShadowDecision.NOT_EXECUTABLE.name() : ev.canonicalDecision());
        } catch (Exception e) {
            decision = CanonicalShadowDecision.NOT_EXECUTABLE;
        }
        Map<String, Object> policyMap = ev.policy() == null ? Map.of() : ev.policy().toMap();
        Map<String, Object> aggregation = ev.aggregationMap();
        aggregation.put("service", SERVICE);

        CanonicalShadowEvaluationEntity saved = persistTerminal(
                app, productionEval, freezeRow.get().getId(), hash,
                ev.status(), decision, ev.reasonCodes(),
                ev.parameterEvidence(), ev.ruleEvidence(), ev.scorecardEvidence(), policyMap, aggregation,
                captureLegacy(productionEval, multi, productionOutcome));
        return saved;
    }

    private Optional<CanonicalShadowEvaluationEntity> existing(UnderwritingEvaluation eval, UUID appId, String hash) {
        if (eval != null && eval.getId() != null) {
            return evaluationRepository.findFirstByUnderwritingEvaluationIdAndIdentityHash(eval.getId(), hash);
        }
        return evaluationRepository.findFirstByApplicationIdAndIdentityHashAndUnderwritingEvaluationIdIsNull(appId, hash);
    }

    private CanonicalShadowEvaluationEntity persistTerminal(
            LoanApplication app,
            UnderwritingEvaluation productionEval,
            UUID freezeRowId,
            String hash,
            String status,
            CanonicalShadowDecision decision,
            List<String> reasons,
            List<Map<String, Object>> parameters,
            List<Map<String, Object>> rules,
            Map<String, Object> scorecard,
            Map<String, Object> policy,
            Map<String, Object> aggregation,
            Map<String, Object> legacy) {
        Optional<CanonicalShadowEvaluationEntity> existing = existing(productionEval, app.getId(), hash);
        if (existing.isPresent()) {
            return existing.get();
        }
        CanonicalShadowEvaluationEntity row = CanonicalShadowEvaluationEntity.builder()
                .id(UUID.randomUUID())
                .applicationId(app.getId())
                .underwritingEvaluationId(productionEval == null ? null : productionEval.getId())
                .freezeRowId(freezeRowId)
                .identityHash(hash == null ? "unresolved" : hash)
                .status(status)
                .canonicalDecision(decision == null ? null : decision.name())
                .reasonCodes(new ArrayList<>(reasons == null ? List.of() : reasons))
                .parameterEvidence(parameters == null ? new ArrayList<>() : new ArrayList<>(parameters))
                .ruleEvidence(rules == null ? new ArrayList<>() : new ArrayList<>(rules))
                .scorecardEvidence(scorecard == null ? new LinkedHashMap<>() : new LinkedHashMap<>(scorecard))
                .policyResult(policy == null ? new LinkedHashMap<>() : new LinkedHashMap<>(policy))
                .aggregation(aggregation == null ? new LinkedHashMap<>() : new LinkedHashMap<>(aggregation))
                .build();
        CanonicalShadowEvaluationEntity saved = evaluationRepository.save(row);

        Map<String, Object> canonicalSide = new LinkedHashMap<>();
        canonicalSide.put("scorecardId", scorecard == null ? null : scorecard.get("scorecardId"));
        canonicalSide.put("numericalScore", scorecard == null ? null : scorecard.get("numericalScore"));
        canonicalSide.put("rules", rules);
        canonicalSide.put("parameters", parameters);
        canonicalSide.put("scorecard", scorecard);
        Map<String, Object> compared = CanonicalShadowComparator.compare(
                legacy, canonicalSide, status, decision);
        CanonicalShadowComparisonEntity cmp = CanonicalShadowComparisonEntity.builder()
                .id(UUID.randomUUID())
                .shadowEvaluationId(saved.getId())
                .applicationId(app.getId())
                .underwritingEvaluationId(productionEval == null ? null : productionEval.getId())
                .identityHash(saved.getIdentityHash())
                .comparisonStatus(String.valueOf(compared.get("comparisonStatus")))
                .legacyDecisionAt(legacyInstant(legacy))
                .canonicalShadowAt(Instant.now())
                .mismatchCounts(mapOf(compared.get("mismatchCounts")))
                .mismatches(listOfMaps(compared.get("mismatches")))
                .policyTestEquivalence(Map.of(
                        "note", "Policy Studio Application Test uses CanonicalObservationalEvaluationService.",
                        "policyTestScorecardAvailable", true,
                        "scorecardConvergenceGap", false))
                .legacyEvidence(legacy)
                .build();
        comparisonRepository.save(cmp);
        log.info("canonical_shadow app={} status={} decision={} comparison={}",
                app.getId(), status, decision, cmp.getComparisonStatus());
        return saved;
    }

    private void persistError(LoanApplication app, UnderwritingEvaluation eval, List<String> reasons, String message) {
        if (app == null) {
            return;
        }
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("error", message);
        persistTerminal(app, eval, null, "error", "ERROR", CanonicalShadowDecision.NOT_EXECUTABLE,
                reasons, List.of(), List.of(), Map.of(), Map.of(), agg, Map.of());
    }

    static Map<String, Object> captureLegacy(
            UnderwritingEvaluation eval, MultiRuleEvalResult multi, String productionOutcome) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("finalDecision", productionOutcome);
        m.put("capturedAt", Instant.now().toString());
        m.put("authority", "UnderwritingRuleEngine+ScorecardPolicyEngine");
        if (eval != null) {
            m.put("underwritingEvaluationId", eval.getId() == null ? null : eval.getId().toString());
            m.put("legacyDecisionTimestamp", eval.getEvaluatedAt() == null ? null : eval.getEvaluatedAt().toString());
            m.put("aggregateDecision", eval.getAggregateDecision());
            m.put("scorecardId", eval.getScorecardId() == null ? null : eval.getScorecardId().toString());
            m.put("scorecardVersion", eval.getScorecardVersion());
            m.put("scorecardPercent", eval.getAggregateScore());
            m.put("scorecardEvidence", eval.getScorecardEvidenceJson());
            m.put("parameterResults", eval.getParameterResultsJson());
            m.put("effectiveValues", eval.getEffectiveValuesJson());
            m.put("selectedSource", eval.getSelectedSourceJson());
            m.put("legacyFallbackUsed", looksLikeLegacyFallback(eval, multi));
        }
        List<Map<String, Object>> rules = new ArrayList<>();
        if (multi != null && multi.perRule() != null) {
            for (MultiRuleEvalResult.PerRuleEval r : multi.perRule()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ruleId", r.ruleId());
                row.put("ruleName", r.ruleName());
                row.put("result", r.creditDecision());
                row.put("policyDecision", r.policyDecision());
                row.put("kind", r.kind());
                rules.add(row);
            }
        } else if (eval != null && eval.getRuleResultsJson() != null) {
            rules.addAll(eval.getRuleResultsJson());
        }
        m.put("rules", rules);
        return m;
    }

    private static boolean looksLikeLegacyFallback(UnderwritingEvaluation eval, MultiRuleEvalResult multi) {
        if (multi == null || !multi.hasAnyRule()) {
            return true;
        }
        if (eval == null) {
            return false;
        }
        Map<String, Object> src = eval.getSelectedSourceJson();
        return src != null && "LEGACY".equalsIgnoreCase(String.valueOf(src.get("underwritingSource")));
    }

    static CanonicalShadowDecision mapDecision(FinalUnderwritingDecision.FinalOutcome outcome) {
        if (outcome == null) {
            return CanonicalShadowDecision.NOT_EXECUTABLE;
        }
        return switch (outcome) {
            case APPROVE -> CanonicalShadowDecision.APPROVE;
            case REJECT -> CanonicalShadowDecision.REJECT;
            case REFER -> CanonicalShadowDecision.MANUAL_REVIEW;
            case DATA_INSUFFICIENT -> CanonicalShadowDecision.DATA_INSUFFICIENT;
            case ERROR, LIVE_BLOCKED -> CanonicalShadowDecision.NOT_EXECUTABLE;
        };
    }

    private static Instant legacyInstant(Map<String, Object> legacy) {
        if (legacy == null) {
            return null;
        }
        Object raw = legacy.get("legacyDecisionTimestamp");
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    static boolean isCanonicalLiveProduction(UnderwritingEvaluation productionEval) {
        if (productionEval == null || productionEval.getSelectedSourceJson() == null) {
            return false;
        }
        Object authority = productionEval.getSelectedSourceJson().get("productionAuthority");
        return CanonicalLiveUnderwritingService.PRODUCTION_AUTHORITY.equals(String.valueOf(authority));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object o) {
        if (o instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object o) {
        if (!(o instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object e : list) {
            if (e instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }
}
