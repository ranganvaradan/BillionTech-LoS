package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.model.ValidationBundle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Underwriter-oriented CreditEvidenceView read model (deterministic sections).
 */
@Service
public class CreditEvidenceViewBuilder {

    private final InvestigationQuestionGenerator questionGenerator;

    public CreditEvidenceViewBuilder(InvestigationQuestionGenerator questionGenerator) {
        this.questionGenerator = questionGenerator != null
                ? questionGenerator
                : new InvestigationQuestionGenerator();
    }

    public CreditEvidenceViewBuilder() {
        this(new InvestigationQuestionGenerator());
    }

    public Map<String, Object> build(
            ValidationBundle bundle,
            Map<String, Object> reconciliations,
            Map<String, Object> dualPolicy,
            Map<String, Object> coverage,
            List<Map<String, Object>> obligationMatches) {
        Map<String, BigDecimal> m = bundle.metricStubs();
        Map<String, Object> view = new LinkedHashMap<>();

        Map<String, Object> coverageSec = new LinkedHashMap<>();
        coverageSec.put("sourcesPresent", new ArrayList<>(bundle.sources().keySet()));
        coverageSec.put("gst", bundle.sources().containsKey("gst"));
        coverageSec.put("itr", bundle.sources().containsKey("itr"));
        coverageSec.put("bank", bundle.sources().containsKey("bank"));
        coverageSec.put("bureau", bundle.sources().containsKey("bureau"));
        coverageSec.put("overallCoveragePct", coverage != null ? coverage.get("overallCoveragePct") : null);
        coverageSec.put("criticalCoveragePct", coverage != null ? coverage.get("criticalCoveragePct") : null);
        view.put("DataCoverage", coverageSec);

        view.put("Identity", Map.of(
                "dataOrigin", bundle.dataOrigin().name(),
                "caseCode", bundle.caseCode().name(),
                "note", "Non-authoritative validation identity — not production KYC"));

        Map<String, Object> bureau = new LinkedHashMap<>();
        bureau.put("available", bundle.sources().containsKey("bureau"));
        bureau.put("emi", m.get("bureau.emi.monthly"));
        bureau.put("liveUnsecuredCount", m.get("bureau.live_unsecured_count"));
        view.put("Bureau", bureau);

        Map<String, Object> banking = new LinkedHashMap<>();
        banking.put("available", bundle.sources().containsKey("bank"));
        banking.put("turnover", m.get("bank.turnover.trailing_12m"));
        banking.put("emi", m.get("bank.emi.monthly"));
        banking.put("abb", m.get("bank.abb.average"));
        view.put("Banking", banking);

        Map<String, Object> gst = new LinkedHashMap<>();
        gst.put("available", bundle.sources().containsKey("gst"));
        gst.put("turnover", m.get("gst.turnover.trailing_12m"));
        view.put("GST", gst);

        Map<String, Object> itr = new LinkedHashMap<>();
        itr.put("available", bundle.sources().containsKey("itr"));
        itr.put("turnover", m.get("itr.turnover.trailing_12m"));
        itr.put("totalIncome", m.get("itr.income.total"));
        itr.put("gti", m.get("itr.income.gti"));
        itr.put("businessProfessionIncome", m.get("itr.income.business_profession"));
        view.put("ITR", itr);

        Map<String, Object> obligations = new LinkedHashMap<>();
        obligations.put("matches", obligationMatches != null ? obligationMatches : List.of());
        obligations.put("bureauEmi", m.get("bureau.emi.monthly"));
        obligations.put("bankEmi", m.get("bank.emi.monthly"));
        obligations.put("declaredEmi", bundle.declaredEmi());
        view.put("Obligations", obligations);

        Map<String, Object> turnover = new LinkedHashMap<>();
        turnover.put("gst", m.get("gst.turnover.trailing_12m"));
        turnover.put("itr", m.get("itr.turnover.trailing_12m"));
        turnover.put("bank", m.get("bank.turnover.trailing_12m"));
        if (reconciliations != null) {
            turnover.put("gstItr", reconciliations.get(ReconciliationConstants.XSRC_GST_ITR_TURNOVER));
            turnover.put("gstBank", reconciliations.get(ReconciliationConstants.XSRC_GST_BANK_TURNOVER));
            turnover.put("itrBank", reconciliations.get(ReconciliationConstants.XSRC_ITR_BANK_TURNOVER));
            turnover.put("triangulation", reconciliations.get(ReconciliationConstants.TURNOVER_TRIANGULATION));
        }
        view.put("TurnoverTriangulation", turnover);

        List<Map<String, Object>> material = new ArrayList<>();
        if (reconciliations != null) {
            for (Map.Entry<String, Object> e : reconciliations.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> rm) {
                    String outcome = String.valueOf(rm.get("outcome"));
                    if (ReconciliationOutcome.MATERIAL_VARIANCE.name().equals(outcome)
                            || ReconciliationOutcome.CONFLICT.name().equals(outcome)
                            || ReconciliationOutcome.DATA_INSUFFICIENT.name().equals(outcome)) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", e.getKey());
                        row.put("outcome", outcome);
                        row.put("detail", rm);
                        material.add(row);
                    }
                }
            }
        }
        view.put("MaterialReconciliations", material);

        Map<String, Object> dq = new LinkedHashMap<>();
        dq.put("incomplete", bundle.caseCode() == ValidationCaseCode.CASE_E_INCOMPLETE
                || bundle.caseCode() == ValidationCaseCode.CASE_B_LEGACY_DEFAULT);
        dq.put("origin", bundle.dataOrigin().name());
        view.put("DataQuality", dq);

        String grade;
        double score;
        if (bundle.caseCode() == ValidationCaseCode.CASE_A_STRONG) {
            grade = "STRONG";
            score = 85;
        } else if (bundle.caseCode() == ValidationCaseCode.CASE_C_TURNOVER_CONFLICT
                || bundle.caseCode() == ValidationCaseCode.CASE_D_OBLIGATION_CONFLICT) {
            grade = "MODERATE";
            score = 55;
        } else {
            grade = "WEAK";
            score = 30;
        }
        view.put("EvidenceStrength", Map.of("grade", grade, "score", score));
        view.put("evidenceStrengthGrade", grade);
        view.put("evidenceStrengthScore", BigDecimal.valueOf(score));

        view.put("LegacyVsCanonical", dualPolicy != null ? dualPolicy : Map.of());
        view.put("OpenInvestigationQuestions", questionGenerator.generate(reconciliations, bundle));
        // Placeholder for P1 shadow Policy Engine section (filled by PolicyEngineEvidenceIntegrator)
        view.putIfAbsent("PolicyEngineShadow", Map.of("shadowOnly", true, "attached", false));
        view.putIfAbsent("CreditDecisionView", Map.of("shadowOnly", true, "attached", false));
        view.putIfAbsent("AiUnderwriterView", Map.of(
                "attached", false,
                "authoritative", false,
                "banner", "AI-generated — non-authoritative",
                "outputMarker", "AI_SUGGESTION"));
        return view;
    }

    /**
     * Merge an optional shadow Policy Engine evaluation section into an existing evidence view.
     */
    public Map<String, Object> withPolicyEngineShadow(Map<String, Object> view, Map<String, Object> policyEngineSection) {
        Map<String, Object> out = view == null ? new LinkedHashMap<>() : new LinkedHashMap<>(view);
        if (policyEngineSection != null) {
            out.put("PolicyEngineShadow", policyEngineSection);
        }
        return out;
    }

    /**
     * Attach P2 shadow Credit Decision View section (§41). Non-authoritative.
     */
    public Map<String, Object> withDecisionView(Map<String, Object> view, Map<String, Object> decisionViewSection) {
        Map<String, Object> out = view == null ? new LinkedHashMap<>() : new LinkedHashMap<>(view);
        if (decisionViewSection != null) {
            out.put("CreditDecisionView", decisionViewSection);
        }
        return out;
    }

    /**
     * Attach A1 assistive AiUnderwriterView (§23). Non-authoritative; never DEMO URL.
     */
    public Map<String, Object> withAiUnderwriterView(Map<String, Object> view, Map<String, Object> aiView) {
        Map<String, Object> out = view == null ? new LinkedHashMap<>() : new LinkedHashMap<>(view);
        if (aiView != null) {
            Map<String, Object> section = new LinkedHashMap<>(aiView);
            section.putIfAbsent("authoritative", false);
            section.putIfAbsent("banner", "AI-generated — non-authoritative");
            section.putIfAbsent("outputMarker", "AI_SUGGESTION");
            section.put("attached", true);
            section.put("demoUrlUsed", false);
            out.put("AiUnderwriterView", section);
        }
        return out;
    }
}
