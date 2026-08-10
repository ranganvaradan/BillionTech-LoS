package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyAuthoringRegistryEnhancementTest {

    @Test
    void exposesRicherBankingAndBureauPaths() {
        PolicyAuthoringRegistry reg = new PolicyAuthoringRegistry();
        assertThat(reg.registry().get("schemaVersion")).isEqualTo("POLICY_AUTHORING_REGISTRY_V2");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) reg.registry().get("metrics");
        assertThat(metrics).anyMatch(m ->
                "banking.avg_daily_balance_3m".equals(m.get("code"))
                        && m.containsKey("unit")
                        && m.containsKey("periodSemantics")
                        && m.containsKey("allowedOperators")
                        && m.containsKey("availability"));
        assertThat(metrics).anyMatch(m -> "bureau.score".equals(m.get("code")));
        assertThat(metrics).anyMatch(m -> "bureau.max_dpd_6m".equals(m.get("code")));
        assertThat(metrics).anyMatch(m -> "bureau.inquiries.current_month".equals(m.get("code")));
        assertThat(metrics).anyMatch(m ->
                "banking.settlement.avg_daily_3m".equals(m.get("code"))
                        && "UNAVAILABLE".equals(m.get("availability")));
        assertThat(reg.hasCanonicalPath("application.proposed_edi")).isTrue();
        assertThat(reg.hasCanonicalPath("invented.path.xyz")).isFalse();
    }
}
