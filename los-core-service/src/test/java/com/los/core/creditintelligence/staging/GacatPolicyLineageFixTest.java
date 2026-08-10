package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionBindingService;
import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionMatcher;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.catalogue.IngestionMatchClassification;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineage;
import com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineageService;
import com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GacatPolicyLineageFixTest {

    @Test
    void bankingBre_ignoredMeansCmOnly_settlementBound_inwardPreserved() throws Exception {
        String text;
        try (var in = GacatPolicyLineageFixTest.class.getClassLoader()
                .getResourceAsStream("policy-fixtures/banking-bre/Banking_BRE.txt")) {
            text = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }

        CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();
        CapabilityIngestionBindingService binder = new CapabilityIngestionBindingService(
                new CapabilityIngestionMatcher(catalogue), catalogue);
        PolicyStudioOrchestrator orchestrator = new PolicyStudioOrchestrator();
        orchestrator.setIngestionBindingService(binder);

        PolicyStudioSession session = orchestrator.processUpload(
                UUID.randomUUID(), "Banking BRE", "TXT", text, "test", null);

        Map<String, Object> view = new LinkedHashMap<>();
        ProspectDay2ViewBuilder.enrich(view, session);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) view.get("ruleCards");
        assertThat(cards).isNotEmpty();

        Map<String, Long> byStatus = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> String.valueOf(c.get("status")), java.util.stream.Collectors.counting()));
        System.out.println("GACAT Banking BRE after counts: total=" + cards.size() + " byStatus=" + byStatus);

        long ignored = cards.stream().filter(c -> "Ignored".equals(c.get("status"))).count();
        assertThat(ignored).as("fresh USER_IGNORED must be 0").isZero();

        long dataReq = cards.stream().filter(c -> "Data requirement".equals(c.get("status"))).count();
        assertThat(dataReq).as("Fields Required lines as data requirements").isGreaterThanOrEqualTo(8);

        long metricAdj = cards.stream().filter(c -> "Metric adjustment".equals(c.get("status"))).count();
        assertThat(metricAdj).as("ADB adjustment clauses").isGreaterThanOrEqualTo(2);

        Map<String, Object> settle = cards.stream()
                .filter(c -> String.valueOf(c.get("systemRuleId")).contains("SETTLEMENT_COUNT")
                        || String.valueOf(c.get("ruleName")).toLowerCase().contains("monthly settlement"))
                .findFirst()
                .orElseThrow();
        assertThat(settle.get("status")).isNotEqualTo("Ignored");
        assertThat(String.valueOf(settle.get("businessRule"))).contains("20");
        assertThat(String.valueOf(settle.getOrDefault("dataAvailability", "")))
                .isIn("DERIVABLE_FROM_AVAILABLE_DATA", "AVAILABLE_AUTOMATICALLY");
        assertThat(String.valueOf(settle.get("resultOnFailure"))).isNotEqualToIgnoringCase("Pass");
        assertThat(String.valueOf(settle.get("blockedReason")))
                .doesNotContain("not available from current data");

        Map<String, Object> inward = cards.stream()
                .filter(c -> String.valueOf(c.get("systemRuleId")).contains("INWARD_RETURN")
                        || String.valueOf(c.get("ruleName")).toLowerCase().contains("inward"))
                .findFirst()
                .orElseThrow();
        assertThat(inward.get("status")).isNotEqualTo("Ignored");
        assertThat(String.valueOf(inward.get("systemRuleId"))).doesNotContain("CHEQUE_BOUNCE");
        assertThat(String.valueOf(inward.get("businessRule")).toLowerCase()).contains("5");

        @SuppressWarnings("unchecked")
        Map<String, Object> banner = (Map<String, Object>) view.get("readinessBanner");
        assertThat(((Number) banner.get("rulesIgnored")).longValue()).isZero();
    }

    @Test
    void bureauBre_noSystemIgnored_incompleteParamsNeedInput() throws Exception {
        String text;
        try (var in = GacatPolicyLineageFixTest.class.getClassLoader()
                .getResourceAsStream("policy-fixtures/bureau-bre/Bureau_BRE.txt")) {
            text = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }

        CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();
        CapabilityIngestionBindingService binder = new CapabilityIngestionBindingService(
                new CapabilityIngestionMatcher(catalogue), catalogue);
        PolicyStudioOrchestrator orchestrator = new PolicyStudioOrchestrator();
        orchestrator.setIngestionBindingService(binder);

        PolicyStudioSession session = orchestrator.processUpload(
                UUID.randomUUID(), "Bureau BRE", "TXT", text, "test", null);

        Map<String, Object> view = new LinkedHashMap<>();
        ProspectDay2ViewBuilder.enrich(view, session);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) view.get("ruleCards");
        assertThat(cards).isNotEmpty();

        long ignored = cards.stream().filter(c -> "Ignored".equals(c.get("status"))).count();
        assertThat(ignored).as("fresh USER_IGNORED must be 0 for Bureau").isZero();

        boolean hasNeeds = cards.stream().anyMatch(c -> {
            String s = String.valueOf(c.get("status"));
            return s.contains("Needs") || "Manual Input".equals(s) || "Manual Review".equals(s);
        });
        assertThat(hasNeeds || cards.stream().anyMatch(c -> "Ready".equals(c.get("status"))))
                .as("Bureau cards should show Ready and/or Needs input — not blanket Ignored")
                .isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> banner = (Map<String, Object>) view.get("readinessBanner");
        assertThat(((Number) banner.get("rulesIgnored")).longValue()).isZero();
    }

    @Test
    void settlementRegistry_availableAndBoundToQrHelper() {
        PolicyAuthoringRegistry reg = new PolicyAuthoringRegistry();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) reg.registry().get("metrics");
        Map<String, Object> count = metrics.stream()
                .filter(m -> "banking.settlement.count_monthly_avg_3m".equals(m.get("code")))
                .findFirst().orElseThrow();
        assertThat(count.get("availability")).isEqualTo("AVAILABLE");
        assertThat(count.get("computeHelperCode")).isEqualTo("banking.qr_settlement.average_monthly_count_3m");
    }

    @Test
    void passFail_neverShowsFailOutcomeAsPass_forGoldenPassDsl() {
        CiPolicyRuleCandidate r = CiPolicyRuleCandidate.builder()
                .systemRuleId("BANK_SMART_SWITCH_SETTLEMENT_COUNT_GTE_20")
                .expression(Map.of("op", "GTE"))
                .onTrue("PASS")
                .onFalse("FAIL")
                .metadata(Map.of("failureTreatment", "REJECT"))
                .build();
        Map<String, Object> pf = PolicyRulePresentationSemantics.passFailPresentation(r);
        assertThat(pf.get("resultOnPass")).isEqualTo("Pass");
        assertThat(pf.get("resultOnFailure")).isEqualTo("Reject");
    }

    @Test
    void excludedFromActivation_isNotIgnored() {
        CiPolicyRuleCandidate r = CiPolicyRuleCandidate.builder()
                .systemRuleId("CLASSIFICATION_DATA_REQUIREMENT")
                .onTrue("INFO")
                .onFalse("INFO")
                .metadata(Map.of(
                        "classification", IngestionMatchClassification.DATA_REQUIREMENT.name(),
                        "excludedFromActivation", true,
                        "dataRequirementOnly", true,
                        "disposition", "EXTRACTED"))
                .build();
        assertThat(PolicyRulePresentationSemantics.ruleStatus(r, null)).isEqualTo("Data requirement");
    }

    @Test
    void inwardMatcher_doesNotMapToChequeBounceMax() {
        CapabilityIngestionMatcher matcher = new CapabilityIngestionMatcher();
        var m = matcher.match(
                "Inward Cheque Return of 5% if transactions are more than 100 in last 3 months or 5 if "
                        + "the transactions are less than 100 for all the bank statement based products.");
        assertThat(m.businessCapabilityId()).isNotEqualTo("BANK.CHEQUE_BOUNCE_MAX");
    }

    @Test
    void metricLineage_settlementPointsToQrHelper() {
        PolicyMetricLineage lineage = new PolicyMetricLineageService()
                .resolve("banking.settlement.count_monthly_avg_3m");
        assertThat(lineage.availability()).isEqualTo(PolicyMetricLineageService.DERIVABLE_FROM_AVAILABLE_DATA);
        assertThat(lineage.computeHelperCode()).isEqualTo("banking.qr_settlement.average_monthly_count_3m");
        assertThat(lineage.classifiedCategory()).isEqualTo("QR_SETTLEMENT");
        assertThat(lineage.derivation()).contains("÷ 3");
    }
}
