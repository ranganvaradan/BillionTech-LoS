package com.los.core.creditintelligence.policystudio.runtime.canonicallive;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalObservationalEvaluation;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalObservationalEvaluationService;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalShadowDecision;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalShadowUnderwritingService;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W11.4 — sole live underwriting authority for Category-governed ordinary applications
 * when {@code credit-intelligence.cutover.allow-canonical-authority=true}.
 * Uses the same {@link CanonicalObservationalEvaluationService} pipeline as Policy Studio Application Test.
 * Fail closed — no legacy fallback.
 */
@Service
@RequiredArgsConstructor
public class CanonicalLiveUnderwritingService {

    public static final String SERVICE = "CanonicalLiveUnderwritingService";
    public static final String PRODUCTION_AUTHORITY = "CANONICAL_LIVE";
    public static final String UNDERWRITING_SOURCE = "CANONICAL";

    private final CreditIntelligenceProperties properties;
    private final CanonicalObservationalEvaluationService observationalEvaluation;

    public record LiveOutcome(
            CanonicalObservationalEvaluation evaluation,
            MultiRuleEvalResult multi,
            String aggregateCreditDecision,
            String policyRecommendation,
            UUID scorecardId,
            Integer scorecardVersion,
            List<Map<String, Object>> parameterResults,
            int riskScore
    ) {}

    public boolean isAuthoritativeFor(LoanApplication app) {
        if (app == null || app.getSelectedCustomerCategoryId() == null) {
            return false;
        }
        CreditIntelligenceProperties.Cutover cutover = properties.getCutover();
        if (cutover == null || !cutover.isAllowCanonicalAuthority()) {
            return false;
        }
        if (notEmpty(cutover.getTenantIds())) {
            String tid = properties.getDefaultTenantId() == null ? "" : properties.getDefaultTenantId().toString();
            if (cutover.getTenantIds().stream().noneMatch(t -> tid.equalsIgnoreCase(t))) {
                return false;
            }
        }
        if (notEmpty(cutover.getProductCodes())) {
            String product = app.getLoanProduct() == null ? "" : app.getLoanProduct();
            if (cutover.getProductCodes().stream().noneMatch(p -> product.equalsIgnoreCase(p))) {
                return false;
            }
        }
        return true;
    }

    public LiveOutcome evaluateAuthoritative(UUID applicationId) {
        CanonicalObservationalEvaluation ev = observationalEvaluation.evaluate(applicationId);
        if (!"COMPLETED".equals(ev.status())) {
            List<String> reasons = ev.reasonCodes() == null ? List.of() : ev.reasonCodes();
            String detail = reasons.isEmpty()
                    ? ev.status()
                    : String.join(", ", reasons);
            throw new BusinessRuleException(
                    "Canonical underwriting cannot execute: " + detail,
                    "CANONICAL_UNDERWRITING_NOT_EXECUTABLE",
                    "UNDERWRITE",
                    Map.of(
                            "status", ev.status(),
                            "reasonCodes", reasons,
                            "identityHash", ev.identityHash() == null ? "" : ev.identityHash(),
                            "productionAuthority", PRODUCTION_AUTHORITY));
        }
        String creditDecision = mapCreditDecision(ev.canonicalDecision());
        MultiRuleEvalResult multi = toMultiRuleEvalResult(ev, creditDecision);
        UUID scorecardId = ev.freeze() == null || ev.freeze().scorecardId() == null
                ? null : ev.freeze().scorecardId();
        Integer scorecardVersion = ev.freeze() == null ? null : ev.freeze().scorecardVersion();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameterResults = ev.scorecardEvidence() == null
                ? List.of()
                : (List<Map<String, Object>>) ev.scorecardEvidence().getOrDefault("parameterResults", List.of());
        int riskScore = riskScoreOf(ev.scorecardEvidence());
        return new LiveOutcome(
                ev,
                multi,
                creditDecision,
                ev.canonicalDecision(),
                scorecardId,
                scorecardVersion,
                parameterResults,
                riskScore);
    }

    static String mapCreditDecision(String canonicalDecision) {
        if (canonicalDecision == null || canonicalDecision.isBlank()) {
            throw new BusinessRuleException(
                    "Canonical underwriting returned no decision",
                    "CANONICAL_UNDERWRITING_NOT_EXECUTABLE",
                    "UNDERWRITE",
                    Map.of("productionAuthority", PRODUCTION_AUTHORITY));
        }
        CanonicalShadowDecision mapped;
        try {
            mapped = CanonicalShadowDecision.valueOf(canonicalDecision);
        } catch (Exception e) {
            throw new BusinessRuleException(
                    "Canonical underwriting returned unknown decision: " + canonicalDecision,
                    "CANONICAL_UNDERWRITING_NOT_EXECUTABLE",
                    "UNDERWRITE",
                    Map.of("canonicalDecision", canonicalDecision, "productionAuthority", PRODUCTION_AUTHORITY));
        }
        return switch (mapped) {
            case APPROVE -> "APPROVED";
            case REJECT -> "REJECTED";
            case MANUAL_REVIEW -> "MANUAL_REVIEW";
            case DATA_INSUFFICIENT -> "DATA_INSUFFICIENT";
            case NOT_EXECUTABLE -> throw new BusinessRuleException(
                    "Canonical underwriting decision is NOT_EXECUTABLE",
                    "CANONICAL_UNDERWRITING_NOT_EXECUTABLE",
                    "UNDERWRITE",
                    Map.of("canonicalDecision", canonicalDecision, "productionAuthority", PRODUCTION_AUTHORITY));
        };
    }

    static MultiRuleEvalResult toMultiRuleEvalResult(CanonicalObservationalEvaluation ev, String creditDecision) {
        List<MultiRuleEvalResult.PerRuleEval> perRule = new ArrayList<>();
        int riskScore = riskScoreOf(ev.scorecardEvidence());
        if (ev.ruleEvidence() != null) {
            for (Map<String, Object> row : ev.ruleEvidence()) {
                if (!Boolean.TRUE.equals(row.get("participates"))) {
                    continue;
                }
                String ruleId = String.valueOf(row.getOrDefault("ruleId", ""));
                String result = String.valueOf(row.getOrDefault("result", ""));
                perRule.add(new MultiRuleEvalResult.PerRuleEval(
                        ruleId,
                        ruleId,
                        result,
                        mapRuleCreditDecision(result, creditDecision),
                        riskScore,
                        row.get("reason") == null ? List.of() : List.of(String.valueOf(row.get("reason"))),
                        "CANONICAL_POLICY_RULE",
                        new LinkedHashMap<>(row),
                        Map.of()));
            }
        }
        Map<String, Object> scorecard = ev.scorecardEvidence() == null ? Map.of() : ev.scorecardEvidence();
        if (!scorecard.isEmpty()) {
            Map<String, Object> matched = new LinkedHashMap<>(scorecard);
            matched.put("engine", "CANONICAL_SCORECARD");
            matched.put("authority", CanonicalShadowUnderwritingService.AGGREGATION_AUTHORITY);
            perRule.add(new MultiRuleEvalResult.PerRuleEval(
                    scorecard.get("scorecardId") == null ? "CANONICAL_SCORECARD" : String.valueOf(scorecard.get("scorecardId")),
                    "Canonical scorecard",
                    String.valueOf(scorecard.getOrDefault("bandOutcome", scorecard.get("outcome"))),
                    creditDecision,
                    riskScore,
                    ev.aggregation() == null ? List.of() : new ArrayList<>(ev.aggregation().reasonCodes()),
                    "SCORECARD",
                    matched,
                    Map.of()));
        }
        List<String> reasons = ev.aggregation() == null ? List.of() : new ArrayList<>(ev.aggregation().reasonCodes());
        if (reasons.isEmpty() && ev.policy() != null && ev.policy().insufficientRuleIds() != null) {
            reasons.addAll(ev.policy().insufficientRuleIds());
        }
        return new MultiRuleEvalResult(
                perRule,
                ev.canonicalDecision(),
                creditDecision,
                riskScore,
                reasons);
    }

    private static String mapRuleCreditDecision(String ruleResult, String aggregateCredit) {
        if ("FAIL".equalsIgnoreCase(ruleResult) || "REJECT".equalsIgnoreCase(ruleResult)) {
            return "REJECTED";
        }
        if ("PASS".equalsIgnoreCase(ruleResult)) {
            return aggregateCredit;
        }
        return aggregateCredit;
    }

    private static int riskScoreOf(Map<String, Object> scorecard) {
        if (scorecard == null) {
            return 0;
        }
        Object raw = scorecard.get("numericalScore");
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return raw == null ? 0 : (int) Math.round(Double.parseDouble(String.valueOf(raw)));
        } catch (Exception e) {
            return 0;
        }
    }

    public static Map<String, Object> freezeSummary(CanonicalApplicationConfiguration freeze, String identityHash) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (freeze != null) {
            m.putAll(freeze.toMap());
        }
        m.put("identityHash", identityHash);
        m.put("productionAuthority", PRODUCTION_AUTHORITY);
        m.put("evaluationService", CanonicalObservationalEvaluationService.SERVICE);
        return m;
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }
}
