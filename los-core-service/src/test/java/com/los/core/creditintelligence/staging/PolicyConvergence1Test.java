package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionBindingService;
import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionMatcher;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.CleanHistoryDefinitionSupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
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

class PolicyConvergence1Test {

    private PolicyStudioSession bankingSession() throws Exception {
        String text;
        try (var in = PolicyConvergence1Test.class.getClassLoader()
                .getResourceAsStream("policy-fixtures/banking-bre/Banking_BRE.txt")) {
            text = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
        CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();
        CapabilityIngestionBindingService binder = new CapabilityIngestionBindingService(
                new CapabilityIngestionMatcher(catalogue), catalogue);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        orch.setIngestionBindingService(binder);
        return orch.processUpload(UUID.randomUUID(), "Banking BRE", "TXT", text, "test", null);
    }

    private PolicyStudioSession bureauSession() throws Exception {
        String text;
        try (var in = PolicyConvergence1Test.class.getClassLoader()
                .getResourceAsStream("policy-fixtures/bureau-bre/Bureau_BRE.txt")) {
            text = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
        CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();
        CapabilityIngestionBindingService binder = new CapabilityIngestionBindingService(
                new CapabilityIngestionMatcher(catalogue), catalogue);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        orch.setIngestionBindingService(binder);
        return orch.processUpload(UUID.randomUUID(), "Bureau BRE", "TXT", text, "test", null);
    }

    @Test
    void policyData_neverEvaluationSource_forBureauRules() throws Exception {
        Map<String, Object> view = new LinkedHashMap<>();
        ProspectDay2ViewBuilder.enrich(view, bureauSession());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) view.get("ruleCards");
        for (Map<String, Object> c : cards) {
            String eval = String.valueOf(c.get("evaluatedFrom"));
            assertThat(eval).as("evaluatedFrom for %s", c.get("systemRuleId"))
                    .doesNotContainIgnoringCase("Policy data");
            assertThat(String.valueOf(c.get("dataSource"))).doesNotContainIgnoringCase("Policy data");
            assertThat(String.valueOf(c.get("dataFamily"))).doesNotContainIgnoringCase("Policy data");
        }
    }

    @Test
    void cleanCompound_evaluatedFromBureau_unresolved_notInvented() throws Exception {
        Map<String, Object> view = new LinkedHashMap<>();
        ProspectDay2ViewBuilder.enrich(view, bureauSession());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> uw = (List<Map<String, Object>>) view.get("underwritingRules");
        Map<String, Object> parent = uw.stream()
                .filter(c -> String.valueOf(c.get("systemRuleId")).contains("OVERDUE_EXCEPTION_PARENT"))
                .findFirst()
                .orElseThrow();
        assertThat(parent.get("evaluatedFrom")).isEqualTo("Bureau");
        assertThat(String.valueOf(parent.get("blockedReason"))).containsIgnoringCase("definition");
        assertThat(parent.get("cleanDefinition")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> clean = (Map<String, Object>) parent.get("cleanDefinition");
        assertThat(clean.get("doNotInvent")).isEqualTo(true);
        assertThat(clean.get("status")).isEqualTo(CleanHistoryDefinitionSupport.STATUS_UNRESOLVED);
        // Children not in primary underwriting list
        assertThat(uw.stream().noneMatch(c -> Boolean.TRUE.equals(c.get("compoundChild")))).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) view.get("compoundChildrenAdvanced");
        assertThat(children.stream().anyMatch(c ->
                String.valueOf(c.get("systemRuleId")).contains("OVERDUE_CHILD"))).isTrue();
    }

    @Test
    void settlement_mapsToExistingQrHelper() {
        var p = new CanonicalParameterRegistry().findById("banking.settlement.count_monthly_avg_3m").orElseThrow();
        assertThat(p.existingImplementationBinding()).isEqualTo("banking.qr_settlement.average_monthly_count_3m");
        assertThat(p.calculationSummary()).contains("÷ 3");
        assertThat(new PolicyAuthoringRegistry().registry().toString())
                .contains("banking.qr_settlement.average_monthly_count_3m");
    }

    @Test
    void banking_dataRequirementsAndMetricAdjustments_notPrimaryUnderwriting() throws Exception {
        Map<String, Object> view = new LinkedHashMap<>();
        ProspectDay2ViewBuilder.enrich(view, bankingSession());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> uw = (List<Map<String, Object>>) view.get("underwritingRules");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) view.get("dataAndCalculations");
        assertThat(uw).isNotEmpty();
        assertThat(data.stream().filter(c -> "Data requirement".equals(c.get("status"))).count())
                .isGreaterThanOrEqualTo(8);
        assertThat(data.stream().filter(c -> "Metric adjustment".equals(c.get("status"))).count())
                .isGreaterThanOrEqualTo(2);
        assertThat(uw.stream().noneMatch(c -> "Data requirement".equals(c.get("status")))).isTrue();
        assertThat(uw.stream().noneMatch(c -> "Metric adjustment".equals(c.get("status")))).isTrue();
        Map<String, Object> settle = uw.stream()
                .filter(c -> String.valueOf(c.get("systemRuleId")).contains("SETTLEMENT_COUNT"))
                .findFirst().orElseThrow();
        assertThat(settle.get("evaluatedFrom")).isEqualTo("Bank Statement");
        assertThat(settle.get("parameterName")).asString().containsIgnoringCase("settlement");
    }

    @Test
    void ignored_onlyViaCmDisposition() {
        assertThat(PolicyStudioConvergencePresenter.isPolicyDataLabel("Policy data")).isTrue();
        assertThat(PolicyStudioConvergencePresenter.normalizeEvalSource("Bureau report")).isEqualTo("Bureau");
    }

    @Test
    void cleanDefinition_notSilentDpdZero() {
        Map<String, Object> def = CleanHistoryDefinitionSupport.buildDraftDefinition(Map.of("notes", "skeleton"));
        assertThat(def.get("silentlyInvented")).isEqualTo(false);
        assertThat(def.get("maximumPermittedDpd")).isNull();
        assertThat(def.get("persistence")).isEqualTo("SESSION_DRAFT_ONLY");
    }

    @Test
    void allowCanonicalAuthority_stillFalse() {
        assertThat(new PolicyAuthoringRegistry().registry().get("allowCanonicalAuthority")).isIn(null, false);
        Map<String, Object> cat = new CanonicalParameterRegistry().catalogueView();
        assertThat(cat.get("allowCanonicalAuthority")).isEqualTo(false);
    }
}
