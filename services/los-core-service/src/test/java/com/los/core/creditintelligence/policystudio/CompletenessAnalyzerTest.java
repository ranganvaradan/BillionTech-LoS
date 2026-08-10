package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CompletenessStatus;
import com.los.core.creditintelligence.policystudio.service.PolicyCompletenessAnalyzer;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompletenessAnalyzerTest {

    @Test
    void bankingBreBlockedWhileAmbiguitiesOpen() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        var s = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking", "TXT", text, "test", null);
        assertThat(s.getCompleteness().get("status")).isEqualTo(CompletenessStatus.BLOCKED.name());
        assertThat(((Number) s.getCompleteness().get("clauses_total")).intValue()).isGreaterThan(10);
        assertThat(((Number) s.getCompleteness().get("test_cases_generated")).intValue()).isGreaterThan(0);
    }

    @Test
    void readyWhenNoOpenAmbiguities() {
        var summary = new PolicyCompletenessAnalyzer().analyze(
                List.of(), List.of(), List.of(MapLike.one()), List.of(),
                List.of(MapLike.one()), List.of(MapLike.one()), List.of(MapLike.one()), List.of());
        assertThat(summary.get("status")).isEqualTo(CompletenessStatus.READY_FOR_REVIEW.name());
    }

    @Test
    void featureFlagOff() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        assertThat(props.getPolicyStudio().isEnabled()).isFalse();
        assertThat(props.getPolicyStudio().isAiEnabled()).isFalse();
        var orch = new PolicyStudioOrchestrator(props, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(orch::assertEnabled)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("disabled");
    }

    /** tiny placeholder for non-empty list sizing */
    private static final class MapLike {
        static Object one() { return new Object(); }
    }
}
