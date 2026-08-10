package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmbiguityResolutionServiceTest {

    @Test
    void resolvePersistsChoice() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession s = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking_BRE", "TXT", text, "author", null);
        var amb = s.getAmbiguities().stream()
                .filter(a -> "EDI".equalsIgnoreCase(a.getPhrase()))
                .findFirst().orElseThrow();
        assertThat(amb.getResolutionStatus()).isEqualTo("OPEN");
        var result = new PolicyStudioOrchestrator().reviewService().resolveAmbiguity(
                s, amb.getId(), "application.proposed_edi", "credit_manager", "Confirmed EDI");
        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(s.ambiguityById(amb.getId()).getResolvedOption()).isEqualTo("application.proposed_edi");
        assertThat(s.ambiguityById(amb.getId()).getResolvedBy()).isEqualTo("credit_manager");
    }
}
