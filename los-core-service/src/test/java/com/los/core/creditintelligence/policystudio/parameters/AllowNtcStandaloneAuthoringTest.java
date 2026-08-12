package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Item 40 — standalone Allow NTC binds boolean bureau.status_ntc. */
class AllowNtcStandaloneAuthoringTest {

    private CmRuleAuthoringService authoring;

    @BeforeEach
    void setUp() {
        CanonicalParameterRegistry.clearInstalledForTests();
        CanonicalParameterRegistry.install(
                CanonicalParameterRegistry.fromSeedForTestsOnly(),
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
        authoring = new CmRuleAuthoringService();
    }

    @Test
    void standaloneAllowNtcBindsStatusNtcBoolean() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "Allow NTC"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("parameterId")).isEqualTo("bureau.status_ntc");
        assertThat(p.get("parameterId")).isNotEqualTo("bureau.thin_file_indicator");
        assertThat(p.get("value")).isEqualTo(true);
        assertThat(String.valueOf(p.get("operator"))).isIn("is", "=", "==");
    }

    @Test
    void compoundAmendmentAlsoAllowNtcStillWorks() {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("kind", "COMPOUND_GROUP");
        proposed.put("combinator", "ANY");
        proposed.put("children", List.of(Map.of(
                "kind", "CONDITION",
                "parameterId", "bureau.score",
                "operator", ">=",
                "value", 650,
                "leftKind", "METRIC")));

        Map<String, Object> a1 = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Also allow NTC",
                "proposedModel", proposed));
        assertThat(a1.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) a1.get("conditions");
        assertThat(children).anyMatch(c -> "bureau.status_ntc".equals(c.get("parameterId")));
        assertThat(children).noneMatch(c -> "bureau.thin_file_indicator".equals(c.get("parameterId")));
    }
}
