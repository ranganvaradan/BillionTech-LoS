package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProspectDay2ViewBuilderTest {

    @Test
    void bankingProspectViewIncludesDay2Cards() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession session = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking BRE", "TXT", text, "test", "Banking_BRE.txt");

        Map<String, Object> out = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(out, session, Map.of("demo", true, "kind", "banking"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ambs = (List<Map<String, Object>>) out.get("ambiguityCards");
        assertThat(ambs).isNotEmpty();
        assertThat(ambs.stream().anyMatch(a -> "EDI".equals(a.get("unclearTerm")))).isTrue();
        assertThat(ambs.stream().anyMatch(a -> String.valueOf(a.get("unclearTerm")).contains("100"))).isTrue();
        assertThat(ambs.get(0).get("typeLabel")).isNotEqualTo("UNKNOWN_BUSINESS_TERM");
        assertThat(ambs.get(0).get("choices")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) out.get("ruleCards");
        assertThat(rules).isNotEmpty();
        assertThat(rules.get(0).get("businessRule")).isNotNull();
        assertThat(rules.get(0).get("visualLogic")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> banner = (Map<String, Object>) out.get("readinessBanner");
        assertThat(banner.get("policyReadinessPercent")).isNotNull();
        assertThat(banner.get("ambiguities")).isNotNull();
    }

    @Test
    void bureauProspectViewIncludesCleanAndCompoundRule() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/bureau-bre/Bureau_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession session = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Bureau BRE", "TXT", text, "test", "Bureau_BRE.txt");

        Map<String, Object> out = new LinkedHashMap<>();
        ProspectPolicyViewBuilder.enrich(out, session, Map.of("demo", true, "kind", "bureau"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ambs = (List<Map<String, Object>>) out.get("ambiguityCards");
        assertThat(ambs.stream().anyMatch(a -> String.valueOf(a.get("unclearTerm")).toLowerCase().contains("clean")))
                .isTrue();
        assertThat(ambs.stream().anyMatch(a -> "NTC".equals(a.get("unclearTerm")))).isTrue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) out.get("ruleCards");
        assertThat(rules.stream().anyMatch(r -> {
            Object vl = r.get("visualLogic");
            return vl instanceof Map<?, ?> m && "EXCEPTION_ALL".equals(m.get("kind"));
        })).isTrue();
    }
}
