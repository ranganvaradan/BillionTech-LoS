package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-DATA-UX-1 — Data & calculations canonical binding for parameter-oriented UI.
 */
class PolicyDataUx1Test {

    @Test
    void adbMetricAdjustmentBindsAffectedParameterWithoutMutatingRegistry() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("status", "Metric adjustment");
        card.put("metricAdjustment", true);
        card.put("ruleName", "Metric adjustment");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("metricAdjustment", true);
        meta.put("affectedMetric", "banking.avg_daily_balance_3m");
        CiPolicyClause clause = CiPolicyClause.builder()
                .sourceText("Any credits from online gaming to be removed from Average Daily Balance calculation.")
                .build();
        PolicyStudioConvergencePresenter.enrichDataCalcCanonicalBinding(card, clause, meta);
        assertThat(card.get("canonicalParameterId")).isEqualTo("banking.avg_daily_balance_3m");
        assertThat(card.get("affectedParameterId")).isEqualTo("banking.avg_daily_balance_3m");
        assertThat(card.get("itemKind")).isEqualTo("CALCULATION_ADJUSTMENT");
        assertThat(String.valueOf(card.get("ruleName"))).containsIgnoringCase("gaming");
        // Registry enterprise entry unchanged
        CanonicalParameterDefinition adb = new CanonicalParameterRegistry()
                .findById("banking.avg_daily_balance_3m").orElseThrow();
        assertThat(adb.calculationSummary()).doesNotContain("gaming");
    }

    @Test
    void largeCreditExposesExactMissingDefinition() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("status", "Data requirement");
        card.put("dataRequirementOnly", true);
        CiPolicyClause clause = CiPolicyClause.builder()
                .sourceText("Party wise Large credits with name and amount of transaction.")
                .build();
        PolicyStudioConvergencePresenter.enrichDataCalcCanonicalBinding(card, clause, Map.of());
        assertThat(card.get("canonicalParameterId")).isEqualTo("banking.large_credit_transactions");
        @SuppressWarnings("unchecked")
        Map<String, Object> missing = (Map<String, Object>) card.get("missingDefinition");
        assertThat(missing.get("question").toString()).contains("Large");
        assertThat(card.get("itemKind")).isEqualTo("REPORT_ANALYST_INFORMATION");
    }

    @Test
    void allowCanonicalAuthorityRemainsFalseOnRegistry() {
        assertThat(new CanonicalParameterRegistry().catalogueView().get("allowCanonicalAuthority"))
                .isEqualTo(false);
    }
}
