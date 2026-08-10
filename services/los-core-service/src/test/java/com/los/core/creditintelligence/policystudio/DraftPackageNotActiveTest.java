package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.domain.DraftPackageStatus;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DraftPackageNotActiveTest {

    @Test
    void draftOnlyNeverProductionActive() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        var orch = new PolicyStudioOrchestrator();
        var s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        var result = orch.buildDraft(s.getDocument().getId(), "author");
        assertThat(result.get("packageStatus")).isEqualTo(DraftPackageStatus.DRAFT_ONLY.name());
        assertThat(result.get("productionActive")).isEqualTo(false);
        assertThat(s.getDraftPackage().getPackageStatus()).isEqualTo(DraftPackageStatus.DRAFT_ONLY.name());
        assertThat(s.getDraftPackage().getContent().get("canActivateProduction")).isEqualTo(false);
    }
}
