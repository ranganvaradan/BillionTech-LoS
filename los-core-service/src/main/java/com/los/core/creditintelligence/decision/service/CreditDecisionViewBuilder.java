package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Underwriter Credit Decision View (§41).
 */
@Service
public class CreditDecisionViewBuilder {

    private final DecisionRecommendationExplanationBuilder explanationBuilder;

    public CreditDecisionViewBuilder(DecisionRecommendationExplanationBuilder explanationBuilder) {
        this.explanationBuilder = explanationBuilder != null
                ? explanationBuilder : new DecisionRecommendationExplanationBuilder();
    }

    public CreditDecisionViewBuilder() {
        this(new DecisionRecommendationExplanationBuilder());
    }

    public Map<String, Object> build(CiCreditRecommendation rec, DecisionRuntimeInput input,
                                     Map<String, Object> legacyComparison) {
        Map<String, Object> view = new LinkedHashMap<>();
        Map<String, Object> policyOutcome = new LinkedHashMap<>();
        policyOutcome.put("overall", input == null ? null : input.policyOverallOutcome());
        policyOutcome.put("recommendationOutcome", rec == null ? null : rec.getRecommendationOutcome());
        view.put("PolicyOutcome", policyOutcome);
        view.put("RiskGrade", dim(rec, "RiskGrade"));
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("amount", input == null ? null : input.requestedAmount());
        requested.put("tenureMonths", input == null ? null : input.requestedTenureMonths());
        requested.put("productCode", input == null ? null : input.productCode());
        view.put("RequestedFacility", requested);
        Map<String, Object> recommended = new LinkedHashMap<>();
        recommended.put("facilityType", rec == null ? null : rec.getRecommendedFacilityType());
        recommended.put("amount", rec == null ? null : rec.getRecommendedAmount());
        recommended.put("tenureMonths", rec == null ? null : rec.getRecommendedTenureMonths());
        recommended.put("emi", rec == null ? null : rec.getRecommendedEmi());
        view.put("RecommendedFacility", recommended);
        view.put("Amount", dim(rec, "Limit"));
        view.put("Tenure", dim(rec, "Tenure"));

        List<Map<String, Object>> components = new ArrayList<>();
        if (rec != null && rec.getPricingComponentResults() != null) {
            for (var c : rec.getPricingComponentResults()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", c.getComponentCode());
                row.put("bps", c.getValueBps());
                components.add(row);
            }
        }
        Map<String, Object> pricing = new LinkedHashMap<>();
        pricing.put("baseRate", rec == null ? null : rec.getRecommendedBaseRate());
        pricing.put("riskPremium", rec == null ? null : rec.getRecommendedRiskPremium());
        pricing.put("finalRate", rec == null ? null : rec.getRecommendedFinalRate());
        pricing.put("components", components);
        view.put("Pricing", pricing);

        view.put("Collateral", rec == null || rec.getRecommendedCollateral() == null
                ? Map.of() : rec.getRecommendedCollateral());
        view.put("Conditions", Map.of(
                "precedent", rec == null || rec.getConditionsPrecedent() == null
                        ? List.of() : rec.getConditionsPrecedent(),
                "subsequent", rec == null || rec.getConditionsSubsequent() == null
                        ? List.of() : rec.getConditionsSubsequent()));
        view.put("Covenants", rec == null || rec.getCovenants() == null ? List.of() : rec.getCovenants());
        view.put("ApprovalAuthority", rec == null || rec.getAuthorityDetail() == null
                ? Map.of() : rec.getAuthorityDetail());
        view.put("KeyEvidence", rec == null || rec.getEvidenceRefs() == null
                ? List.of() : rec.getEvidenceRefs());
        view.put("MaterialConflicts", extractConflicts(input));
        view.put("LegacyComparison", legacyComparison == null ? Map.of() : legacyComparison);
        view.put("Explanation", explanationBuilder.build(rec));
        view.put("shadowOnly", true);
        view.put("authoritative", false);
        view.put("humanDecisionBoundary", Map.of(
                "table", "ci_human_credit_decision",
                "p2Writes", false,
                "note", "Human final decision is future handoff — P2 never writes automatically"));
        return view;
    }

    private Object dim(CiCreditRecommendation rec, String key) {
        if (rec == null || rec.getDimensions() == null) {
            return Map.of();
        }
        return rec.getDimensions().getOrDefault(key, Map.of());
    }

    private List<Object> extractConflicts(DecisionRuntimeInput input) {
        List<Object> conflicts = new ArrayList<>();
        if (input == null) {
            return conflicts;
        }
        for (Map.Entry<String, Object> e : input.reconciliations().entrySet()) {
            if (e.getValue() instanceof Map<?, ?> rm) {
                String outcome = String.valueOf(rm.get("outcome"));
                if ("CONFLICT".equalsIgnoreCase(outcome) || "MATERIAL_VARIANCE".equalsIgnoreCase(outcome)) {
                    conflicts.add(Map.of("code", e.getKey(), "outcome", outcome));
                }
            }
        }
        return conflicts;
    }
}
