package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.service.DraftPolicySimulator;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftPolicySimulatorTest {

    @Test
    void labelsValidationFixtureSimulation() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/bureau-bre/Bureau_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        var s = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Bureau", "TXT", text, "test", null);
        var sim = new DraftPolicySimulator().simulate(s);
        assertThat(sim.get("label")).isEqualTo(DraftPolicySimulator.LABEL);
        assertThat(sim.get("disclaimer").toString()).containsIgnoringCase("not portfolio");
        assertThat(sim.get("casesSimulated")).isEqualTo(5);
        assertThat(sim.get("rows")).isInstanceOf(java.util.List.class);
    }
}
