package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.validation.service.MultiSourceValidationHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StagingDemoWorkspaceTest {

    private StagingDemoWorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getValidation().setEnabled(true);
        props.getValidation().setPersistRuns(false);
        props.getStagingDemo().setEnabled(true);
        props.getCutover().setAllowCanonicalAuthority(false);
        MultiSourceValidationHarness harness = new MultiSourceValidationHarness(props);
        workspaceService = new StagingDemoWorkspaceService(props, harness);
    }

    @Test
    void buildWorkspace_caseA_stampsFixtureSafetyAndDecision() {
        Map<String, Object> ws = workspaceService.buildWorkspace("CASE_A");

        assertThat(ws.get("fixtureBanner")).isEqualTo(StagingCaseCatalog.FIXTURE_BANNER);
        assertThat(ws.get("productionActive")).isEqualTo(false);
        assertThat(ws.get("authoritative")).isEqualTo(false);
        assertThat(ws.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(ws.get("caseCode")).isEqualTo("CASE_A");
        assertThat(ws.get("creditEvidenceView")).isInstanceOf(Map.class);
        assertThat(ws.get("creditDecisionView")).isInstanceOf(Map.class);
        assertThat(ws.get("aiUnderwriterView")).isInstanceOf(Map.class);
        assertThat(ws.get("legacyVsCanonical")).isInstanceOf(Map.class);
        assertThat(ws.get("decisionExplanation")).isInstanceOf(Map.class);
        assertThat(ws.get("policyResult")).isInstanceOf(Map.class);
        assertThat(ws.get("recommendation")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> rec = (Map<String, Object>) ws.get("recommendation");
        assertThat(rec.get("authoritative")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        Map<String, Object> replay = (Map<String, Object>) ws.get("replay");
        assertThat(replay.get("match")).isEqualTo(true);
    }

    @Test
    void buildWorkspace_allCases_succeedWithFixtureBanner() {
        for (String code : List.of("CASE_A", "CASE_B", "CASE_C", "CASE_D", "CASE_E")) {
            Map<String, Object> ws = workspaceService.buildWorkspace(code);
            assertThat(ws.get("fixtureBanner")).isEqualTo(StagingCaseCatalog.FIXTURE_BANNER);
            assertThat(ws.get("caseCode")).isEqualTo(code);
            assertThat(ws.get("aiUnderwriterView")).isInstanceOf(Map.class);
            assertThat(ws.get("decisionExplanation")).isInstanceOf(Map.class);
        }
    }

    @Test
    void listCases_returnsFiveFixtures() {
        assertThat(workspaceService.listCases()).hasSize(5);
        assertThat(workspaceService.listCases().get(0).get("fixtureBanner"))
                .isEqualTo(StagingCaseCatalog.FIXTURE_BANNER);
    }
}
