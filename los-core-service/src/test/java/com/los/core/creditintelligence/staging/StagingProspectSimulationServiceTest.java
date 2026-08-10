package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StagingProspectSimulationServiceTest {

    private PolicyStudioOrchestrator orch;
    private StagingProspectSimulationService sim;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        orch = new PolicyStudioOrchestrator();
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setDefaultTenantId(tenantId);
        sim = new StagingProspectSimulationService(props, orch);
    }

    @Test
    void listsTenCalibratedDemoApplications() {
        Map<String, Object> apps = sim.listApplications("VALIDATION_FIXTURES");
        assertThat(apps.get("count")).isEqualTo(10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) apps.get("applications");
        assertThat(list).hasSize(10);
        assertThat(list).allMatch(a -> a.get("product") != null && !String.valueOf(a.get("product")).isBlank());
        assertThat(list).allMatch(a -> a.get("scenarioLabel") != null);
        assertThat(apps.get("fixtureBanner").toString()).contains("VALIDATION FIXTURE");
        assertThat(apps.get("demoResolutionBanner").toString()).contains("CUSTOMER CONFIRMATION REQUIRED");
        assertThat(apps.get("demoCaseGroups")).isInstanceOf(List.class);
    }

    @Test
    void digiLeapDoesNotExecuteSmartSwitchOnlyRule() {
        assertThat(StagingProspectSimulationService.ruleAppliesToProduct(
                rule("BANK_SMART_SWITCH_SETTLEMENT_COUNT_GTE_20", List.of("SMART_SWITCH")),
                "DIGILEAP")).isFalse();
        assertThat(StagingProspectSimulationService.ruleAppliesToProduct(
                rule("BANK_DIGILEAP_TXN_GTE_20", List.of("DIGILEAP")),
                "DIGILEAP")).isTrue();
        assertThat(StagingProspectSimulationService.ruleAppliesToProduct(
                rule("BANK_INWARD_RETURN_BRANCHED_100", List.of("ALL_BANK_STATEMENT")),
                "DIGILEAP")).isTrue();
    }

    @Test
    void starterDoesNotExecuteReboostOnlyRule() {
        assertThat(StagingProspectSimulationService.ruleAppliesToProduct(
                rule("BANK_REBOOST_TXN_GTE_30_IF_AMT_GT_60000", List.of("REBOOST")),
                "STARTER")).isFalse();
        assertThat(StagingProspectSimulationService.ruleAppliesToProduct(
                rule("BANK_STARTER_ADB_GTE_EDI", List.of("STARTER")),
                "STARTER")).isTrue();
    }

    @Test
    void runsBankingDraftAgainstTenAppsWithScopingAndSpread() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession session = orch.processUpload(
                tenantId, "Banking BRE", "TXT", text, "test", "Banking_BRE.txt");
        UUID docId = session.getDocument().getId();

        Map<String, Object> ctx = sim.simulationContext(docId, null);
        assertThat(ctx.get("coverageMatrix")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> matrix = (Map<String, Object>) ctx.get("coverageMatrix");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrixApps = (List<Map<String, Object>>) matrix.get("applications");
        Map<String, Object> digi = matrixApps.stream()
                .filter(a -> "APP_001_STRONG_DIGILEAP".equals(a.get("applicationCode")))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> nonApp = (List<String>) digi.get("nonApplicableRules");
        assertThat(nonApp).anyMatch(id -> id.contains("SMART_SWITCH"));
        assertThat(nonApp).anyMatch(id -> id.contains("STARTER") || id.contains("REBOOST"));

        Map<String, Object> result = sim.runSimulation(docId, Map.of(
                "applicationCodes", StagingProspectSimulationCatalog.tenDemoApps().stream()
                        .map(StagingProspectSimulationCatalog.DemoApp::applicationCode).toList(),
                "reviewer", "credit_manager"), null);

        assertThat(result.get("simulationBanner").toString()).contains("NON-AUTHORITATIVE");
        assertThat(result.get("allowCanonicalAuthority")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> agg = (Map<String, Object>) result.get("aggregates");
        assertThat(agg.get("applicationsTested")).isEqualTo(10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> apps = (List<Map<String, Object>>) result.get("applications");
        assertThat(apps).hasSize(10);

        Map<String, String> byCode = new java.util.LinkedHashMap<>();
        for (Map<String, Object> a : apps) {
            byCode.put(String.valueOf(a.get("applicationCode")), String.valueOf(a.get("policyResult")));
            assertThat(a.get("scenarioLabel")).isNotNull();
            assertThat(a.get("product")).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> drill = (Map<String, Object>) a.get("drillDown");
            assertThat(drill.get("applicableRules")).isInstanceOf(List.class);
            assertThat(drill.get("nonApplicableRules")).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> skipped = (List<String>) drill.get("nonApplicableRules");
            if ("DIGILEAP".equals(a.get("product"))) {
                assertThat(skipped).anyMatch(n -> n.toLowerCase().contains("smart switch")
                        || n.toLowerCase().contains("starter")
                        || n.toLowerCase().contains("reboost"));
            }
        }

        assertThat(byCode.get("APP_001_STRONG_DIGILEAP")).isEqualTo("PASS");
        assertThat(byCode.get("APP_003_STARTER_STRONG")).isEqualTo("PASS");
        assertThat(byCode.get("APP_004_HIGH_INQUIRIES")).isEqualTo("FAIL");
        assertThat(byCode.get("APP_005_DPD_FAIL")).isEqualTo("FAIL");
        assertThat(byCode.get("APP_006_TURNOVER_CONFLICT")).isEqualTo("REFER");
        assertThat(byCode.get("APP_007_OBLIGATION_CONFLICT")).isEqualTo("REFER");
        assertThat(byCode.get("APP_008_MISSING_BANKING")).isEqualTo("DATA INSUFFICIENT");

        Map<String, Object> passApp = apps.stream()
                .filter(a -> "APP_001_STRONG_DIGILEAP".equals(a.get("applicationCode")))
                .findFirst().orElseThrow();
        assertThat(String.valueOf(passApp.get("topReason")).toLowerCase()).doesNotContain("policy failed");
        assertThat(String.valueOf(passApp.get("topReason"))).containsIgnoringCase("DigiLeap");

        Map<String, Object> failApp = apps.stream()
                .filter(a -> "APP_004_HIGH_INQUIRIES".equals(a.get("applicationCode")))
                .findFirst().orElseThrow();
        assertThat(String.valueOf(failApp.get("topReason")).toLowerCase()).contains("transaction");
        @SuppressWarnings("unchecked")
        Map<String, Object> failDrill = (Map<String, Object>) failApp.get("drillDown");
        assertThat(failDrill.get("bindingRule")).isNotNull();
        assertThat(String.valueOf(failApp.get("topReason")))
                .contains(String.valueOf(failDrill.get("bindingRule")));

        Map<String, Object> tight = apps.stream()
                .filter(a -> "APP_002_DIGILEAP_CAPACITY_TIGHT".equals(a.get("applicationCode")))
                .findFirst().orElseThrow();
        assertThat(tight.get("policyResult")).isEqualTo("PASS");
        assertThat(String.valueOf(tight.get("recommendationCode"))).isIn("COUNTER_OFFER", "APPROVE_WITH_CONDITIONS", "APPROVE");

        long pass = apps.stream().filter(a -> "PASS".equals(a.get("policyResult"))).count();
        long fail = apps.stream().filter(a -> "FAIL".equals(a.get("policyResult"))).count();
        long refer = apps.stream().filter(a -> "REFER".equals(a.get("policyResult"))).count();
        long di = apps.stream().filter(a -> String.valueOf(a.get("policyResult")).contains("INSUFFICIENT")).count();
        assertThat(pass).isGreaterThanOrEqualTo(2);
        assertThat(fail).isGreaterThanOrEqualTo(2);
        assertThat(refer).isGreaterThanOrEqualTo(2);
        assertThat(di).isGreaterThanOrEqualTo(1);

        String runId = String.valueOf(result.get("runId"));
        byte[] csv = sim.exportCsv(docId, UUID.fromString(runId), null);
        assertThat(new String(csv, StandardCharsets.UTF_8)).contains("application,policy_result");
    }

    @Test
    void runsBureauDraftWithExpectedTendencies() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/bureau-bre/Bureau_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession session = orch.processUpload(
                tenantId, "Bureau BRE", "TXT", text, "test", "Bureau_BRE.txt");
        UUID docId = session.getDocument().getId();

        Map<String, Object> result = sim.runSimulation(docId, Map.of(
                "applicationCodes", List.of(
                        "APP_001_STRONG_DIGILEAP",
                        "APP_004_HIGH_INQUIRIES",
                        "APP_005_DPD_FAIL",
                        "APP_009_MISSING_BUREAU"),
                "reviewer", "credit_manager"), null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> apps = (List<Map<String, Object>>) result.get("applications");
        Map<String, String> byCode = new java.util.LinkedHashMap<>();
        apps.forEach(a -> byCode.put(String.valueOf(a.get("applicationCode")), String.valueOf(a.get("policyResult"))));

        assertThat(byCode.get("APP_001_STRONG_DIGILEAP")).isEqualTo("PASS");
        assertThat(byCode.get("APP_004_HIGH_INQUIRIES")).isEqualTo("FAIL");
        assertThat(byCode.get("APP_005_DPD_FAIL")).isEqualTo("FAIL");
        assertThat(byCode.get("APP_009_MISSING_BUREAU")).isIn("DATA INSUFFICIENT", "REFER");
    }

    private static CiPolicyRuleCandidate rule(String id, List<String> products) {
        return CiPolicyRuleCandidate.builder()
                .systemRuleId(id)
                .scope(Map.of("products", products))
                .build();
    }
}
