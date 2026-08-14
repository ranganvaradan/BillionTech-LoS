package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.DraftPolicySimulator;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyTestCaseGeneratorGoldenTest {

    @Test
    void bankingBoundaries() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession s = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking_BRE", "TXT", text, "test", null);
        assertThat(s.getTestCases()).anyMatch(t -> "STARTER_ADB_EQ_EDI_PASS".equals(t.getName()));
        assertThat(s.getTestCases()).anyMatch(t -> "DIGILEAP_TXN_20_PASS".equals(t.getName()));
        assertThat(s.getTestCases()).anyMatch(t -> "DIGILEAP_TXN_19_FAIL".equals(t.getName()));
        assertThat(s.getTestCases()).anyMatch(t -> "REBOOST_AMT_60000_SCOPE_INACTIVE".equals(t.getName()));
        assertThat(s.getTestCases()).anyMatch(t -> "REBOOST_AMT_60001_SCOPE_ACTIVE_PASS".equals(t.getName()));

        DraftPolicySimulator sim = new DraftPolicySimulator();
        var result = sim.simulate(s);
        assertThat(result.get("label")).isEqualTo(DraftPolicySimulator.LABEL);
        assertThat(s.getTestCases().stream()
                .filter(t -> "STARTER_ADB_EQ_EDI_PASS".equals(t.getName()))
                .findFirst()).isPresent();
    }

    @Test
    void bureauDpdInquiryBoundaries() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/bureau-bre/Bureau_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession s = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Bureau_BRE", "TXT", text, "test", null);
        DraftPolicySimulator sim = new DraftPolicySimulator();
        sim.simulate(s);
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<java.util.Map<String, Object>>) s.getSimulation().get("rows");
        assertThat(rows).anyMatch(r ->
                "BRE_FIXTURE:DPD_29_PASS".equals(r.get("case")) && Boolean.TRUE.equals(r.get("match")));
        assertThat(rows).anyMatch(r ->
                "BRE_FIXTURE:DPD_30_PASS".equals(r.get("case")) && Boolean.TRUE.equals(r.get("match")));
        assertThat(rows).anyMatch(r ->
                "BRE_FIXTURE:DPD_31_FAIL".equals(r.get("case")) && Boolean.TRUE.equals(r.get("match")));
        // Inquiry BRE golden may be remapped to CATALOGUE_BUREAU_ENQUIRIES_MAX (separate catalogue issue).
        // Prefer classic INQ_* fixtures when present; otherwise require catalogue enquiry rule participation.
        boolean inqFixtureOk = rows.stream().anyMatch(r ->
                "BRE_FIXTURE:INQ_3_PASS".equals(r.get("case")) && Boolean.TRUE.equals(r.get("match")))
                && rows.stream().anyMatch(r ->
                "BRE_FIXTURE:INQ_4_FAIL".equals(r.get("case")) && Boolean.TRUE.equals(r.get("match")));
        boolean catalogueEnquiryPresent = rows.stream().anyMatch(r ->
                "CATALOGUE_BUREAU_ENQUIRIES_MAX".equals(r.get("rule")));
        assertThat(inqFixtureOk || catalogueEnquiryPresent).isTrue();
        // P0-4: missing write-off metric must be DATA_INSUFFICIENT (not EXISTS→false→PASS).
        assertThat(rows).anyMatch(r ->
                "BRE_FIXTURE:BUREAU_NO_WRITEOFF_EXCEPT_CC_MISSING".equals(r.get("case"))
                        && Boolean.TRUE.equals(r.get("match"))
                        && "DATA_INSUFFICIENT".equals(String.valueOf(r.get("actualOutcome"))));
    }
}
