package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * POLICY-DATA-RESOLUTION-UX-1 — typed policy-scoped Data & Calculations resolutions.
 */
class PolicyDataResolutionUx1Test {

    @Test
    void largeCredit_threshold_persistsPolicyScoped_informationOnly() {
        Map<String, Object> res = PolicyDataResolutionSupport.thresholdDefinition(
                "banking.large_credit_transactions",
                new BigDecimal("500000"),
                "SME term loan convention",
                "credit_manager",
                "doc-1");
        assertThat(res.get("resolutionType")).isEqualTo(PolicyDataResolutionSupport.TYPE_POLICY_THRESHOLD);
        assertThat(res.get("businessDefinitionStatus")).isEqualTo(PolicyDataResolutionSupport.BIZ_DEFINED);
        assertThat(res.get("executionStatus")).isEqualTo(PolicyDataResolutionSupport.EXEC_INFORMATION_ONLY);
        assertThat(res.get("executionImpact")).isEqualTo(PolicyDataResolutionSupport.IMPACT_NON_BLOCKING);
        assertThat(res.get("cmStatus")).isEqualTo("READY");
        assertThat(res.get("gacatMutated")).isEqualTo(false);
        assertThat(res.get("allowCanonicalAuthority")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> def = (Map<String, Object>) res.get("definition");
        assertThat(def.get("amountInr")).isEqualTo(new BigDecimal("500000"));
        assertThat(String.valueOf(res.get("howDefined"))).contains("500,000");
    }

    @Test
    void largeCredit_invalidAmount_rejected() {
        assertThatThrownBy(() -> PolicyDataResolutionSupport.thresholdDefinition(
                "banking.large_credit_transactions", BigDecimal.ZERO, null, "cm", "d"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intercompany_businessDefined_executionNeedsConfig() {
        Map<String, Object> res = PolicyDataResolutionSupport.classificationDefinition(
                "banking.intercompany_transactions",
                "RELATED_PARTY_LIST_NOT_CONFIGURED",
                null,
                "cm",
                "doc-1");
        assertThat(res.get("businessDefinitionStatus")).isEqualTo(PolicyDataResolutionSupport.BIZ_DEFINED);
        assertThat(res.get("executionStatus")).isEqualTo(PolicyDataResolutionSupport.EXEC_NEEDS_CONFIG);
        assertThat(res.get("cmStatus")).isEqualTo("NEEDS_CONFIGURATION");
        assertThat(String.valueOf(res.get("message"))).containsIgnoringCase("no production");
        assertThat(res.get("executionImpact")).isEqualTo(PolicyDataResolutionSupport.IMPACT_NON_BLOCKING);
    }

    @Test
    void emiBounce_saveWithBinding_becomesReady() {
        Map<String, Object> res = PolicyDataResolutionSupport.calculationConfiguration(
                "banking.emi_bounce_count_3m", "cm", "doc-1", Map.of(
                        "periodMonths", 3,
                        "emiIdentification", "EXISTING_EMI_CLASSIFIER",
                        "bounceIdentification", "EXISTING_BOUNCE_RETURN_CLASSIFIER",
                        "confirmExecutable", true));
        assertThat(res.get("executionStatus")).isEqualTo(PolicyDataResolutionSupport.EXEC_READY);
        assertThat(res.get("cmStatus")).isEqualTo("READY");
        assertThat(res.get("executionCapabilityAvailable")).isEqualTo(true);
        assertThat(res.get("gacatMutated")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> def = (Map<String, Object>) res.get("definition");
        assertThat(def.get("executableMetric")).isEqualTo(true);
        assertThat(def.get("binding")).isEqualTo("EmiBounceCountCalculator.V1");
        assertThat(def.get("periodMonths")).isEqualTo(3);
    }

    @Test
    void emiBounce_designProposalOnly_staysNeedsConfiguration() {
        Map<String, Object> res = PolicyDataResolutionSupport.calculationConfiguration(
                "banking.emi_bounce_count_3m", "cm", "doc-1", Map.of("confirmExecutable", false));
        assertThat(res.get("cmStatus")).isEqualTo("NEEDS_CONFIGURATION");
        @SuppressWarnings("unchecked")
        Map<String, Object> def = (Map<String, Object>) res.get("definition");
        assertThat(def.get("executableMetric")).isEqualTo(false);
    }

    @Test
    void unknownCalculation_cannotBecomeReady() {
        Map<String, Object> res = PolicyDataResolutionSupport.calculationConfiguration(
                "banking.unknown_metric_xyz", "cm", "doc-1");
        assertThat(res.get("cmStatus")).isEqualTo("NEEDS_CONFIGURATION");
        assertThat(res.get("executionCapabilityAvailable")).isEqualTo(false);
        assertThat(String.valueOf(res.get("displayStatus"))).containsIgnoringCase("IMPLEMENTATION");
    }

    @Test
    void adbBulkAdjustment_needsConfiguration_blockingFlag() {
        Map<String, Object> res = PolicyDataResolutionSupport.policyAdjustment(
                "banking.adb_bulk_deposit_adjustment", new BigDecimal("10"), "cm", "doc-1");
        assertThat(res.get("resolutionType")).isEqualTo(PolicyDataResolutionSupport.TYPE_POLICY_ADJUSTMENT);
        assertThat(res.get("cmStatus")).isEqualTo("NEEDS_CONFIGURATION");
        assertThat(res.get("executionImpact")).isEqualTo(PolicyDataResolutionSupport.IMPACT_BLOCKING);
        assertThat(res.get("gacatMutated")).isEqualTo(false);
    }

    @Test
    void manual_requiresCaptureStageForReady() {
        Map<String, Object> bad = PolicyDataResolutionSupport.manualInput(
                "application.custom_metric", "Custom", "Money", "Analyst", "UNSPECIFIED", "doc-1");
        assertThat(bad.get("cmStatus")).isEqualTo("NEEDS_CONFIGURATION");

        Map<String, Object> ok = PolicyDataResolutionSupport.manualInput(
                "application.custom_metric", "Custom", "Money", "Analyst", "CAM_UNDERWRITING_REVIEW", "doc-1");
        assertThat(ok.get("cmStatus")).isEqualTo("MANUAL_INPUT");
        assertThat(ok.get("executionStatus")).isEqualTo(PolicyDataResolutionSupport.EXEC_MANUAL);
    }

    @Test
    void applyToCards_attachesHowDefined_clearsOpenQuestion() {
        Map<String, Object> res = PolicyDataResolutionSupport.thresholdDefinition(
                "banking.large_credit_transactions", new BigDecimal("500000"), null, "cm", "d");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("canonicalParameterId", "banking.large_credit_transactions");
        card.put("missingDefinition", Map.of(
                "question", "What qualifies as a \"Large\" credit?",
                "action", "DEFINE"));
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card);
        PolicyDataResolutionSupport.applyToDataCalcCards(cards, Map.of(
                "banking.large_credit_transactions", res));
        assertThat(card.get("dataCalcResolved")).isEqualTo(true);
        assertThat(String.valueOf(card.get("howDefined"))).contains("500,000");
        @SuppressWarnings("unchecked")
        Map<String, Object> missing = (Map<String, Object>) card.get("missingDefinition");
        assertThat(missing.get("resolved")).isEqualTo(true);
    }

    @Test
    void versionClone_documentMetaKeyPreservedIndependently() {
        // Simulate v1 and v2 maps — editing v2 must not mutate v1 object graph after copy
        Map<String, Object> v1Res = PolicyDataResolutionSupport.thresholdDefinition(
                "banking.large_credit_transactions", new BigDecimal("500000"), null, "cm", "v1");
        Map<String, Object> v1Doc = new LinkedHashMap<>();
        Map<String, Object> v1Map = new LinkedHashMap<>();
        v1Map.put("banking.large_credit_transactions", new LinkedHashMap<>(v1Res));
        v1Doc.put(PolicyDataResolutionSupport.DOC_META_KEY, v1Map);

        Map<String, Object> v2Doc = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> cloned = new LinkedHashMap<>(
                (Map<String, Object>) ((Map<?, ?>) v1Doc.get(PolicyDataResolutionSupport.DOC_META_KEY)));
        Map<String, Object> deep = new LinkedHashMap<>();
        cloned.forEach((k, v) -> deep.put(k, new LinkedHashMap<>((Map<String, Object>) v)));
        v2Doc.put(PolicyDataResolutionSupport.DOC_META_KEY, deep);

        Map<String, Object> v2Res = PolicyDataResolutionSupport.thresholdDefinition(
                "banking.large_credit_transactions", new BigDecimal("750000"), null, "cm", "v2");
        @SuppressWarnings("unchecked")
        Map<String, Object> v2Map = (Map<String, Object>) v2Doc.get(PolicyDataResolutionSupport.DOC_META_KEY);
        v2Map.put("banking.large_credit_transactions", v2Res);

        @SuppressWarnings("unchecked")
        Map<String, Object> stillV1 = (Map<String, Object>)
                ((Map<?, ?>) v1Doc.get(PolicyDataResolutionSupport.DOC_META_KEY))
                        .get("banking.large_credit_transactions");
        @SuppressWarnings("unchecked")
        Map<String, Object> v1Def = (Map<String, Object>) stillV1.get("definition");
        assertThat(v1Def.get("amountInr")).isEqualTo(new BigDecimal("500000"));
        @SuppressWarnings("unchecked")
        Map<String, Object> v2Def = (Map<String, Object>) v2Res.get("definition");
        assertThat(v2Def.get("amountInr")).isEqualTo(new BigDecimal("750000"));
    }
}
