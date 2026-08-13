package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Items 24–25 — outer AND with parenthesized/grouped OR must not collapse to top-level OR.
 */
class NestedAndOrParenthesesTest {

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
    void item24_andWithParenthesizedOr_keepsTopLevelAnd() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score >= 700 AND (FOIR <= 50 OR LTV <= 60)"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("combinator")).isEqualTo("ALL");
        assertThat(String.valueOf(((Map<?, ?>) p.get("expression")).get("op")))
                .isEqualToIgnoringCase("AND");
        assertNestedOrPresent(p);
    }

    @Test
    void item25_andOfTwoParenthesizedOrGroups_keepsTopLevelAnd() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "(bureau score >= 700 OR bureau score = -1) AND (FOIR <= 50 OR LTV <= 60)"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("combinator")).isEqualTo("ALL");
        assertThat(String.valueOf(((Map<?, ?>) p.get("expression")).get("op")))
                .isEqualToIgnoringCase("AND");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) p.get("children");
        assertThat(children).hasSize(2);
        assertThat(children).allMatch(c ->
                CompoundExpressionAuthoringSupport.KIND_GROUP.equalsIgnoreCase(String.valueOf(c.get("kind")))
                        && "ANY".equalsIgnoreCase(String.valueOf(c.get("combinator"))));
    }

    @Test
    void andEitherControl_stillProducesAndWithNestedOr() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score >= 700 and either FOIR <= 50 or LTV <= 60"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("combinator")).isEqualTo("ALL");
        assertThat(String.valueOf(((Map<?, ?>) p.get("expression")).get("op")))
                .isEqualToIgnoringCase("AND");
        assertNestedOrPresent(p);
    }

    @Test
    void bracketedOrGroup_joinedByAnd() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score >= 700 AND [FOIR <= 50 OR LTV <= 60]"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) p.get("expression")).get("op")))
                .isEqualToIgnoringCase("AND");
        assertNestedOrPresent(p);
    }

    @Test
    void lowercaseAndWithParenthesizedOrGroup() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "FOIR <= 50 and (bureau score >= 700 OR bureau score = -1)"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) p.get("expression")).get("op")))
                .isEqualToIgnoringCase("AND");
        assertNestedOrPresent(p);
    }

    @Test
    void originalTopLevelOrAndCasesUnaffected() {
        // 16/17-style OR
        Map<String, Object> or = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score > 700 OR bureau score IS EQUAL TO -1"));
        assertThat(or.get("complete")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) or.get("expression")).get("op")))
                .isEqualToIgnoringCase("OR");

        // 19-style OR with equals phrasing
        Map<String, Object> orEq = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score > 700 OR bureau score IS EQUAL TO -1"));
        assertThat(orEq.get("complete")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) orEq.get("expression")).get("op")))
                .isEqualToIgnoringCase("OR");

        // 21-style flat AND (no nested OR group)
        Map<String, Object> and = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score >= 700 AND FOIR <= 50"));
        assertThat(and.get("complete")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) and.get("expression")).get("op")))
                .isEqualToIgnoringCase("AND");
    }

    private static void assertNestedOrPresent(Map<String, Object> p) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) p.get("children");
        assertThat(children).isNotNull();
        boolean nestedOr = children.stream().anyMatch(c ->
                CompoundExpressionAuthoringSupport.KIND_GROUP.equalsIgnoreCase(String.valueOf(c.get("kind")))
                        && "ANY".equalsIgnoreCase(String.valueOf(c.get("combinator"))));
        assertThat(nestedOr).as("expected nested OR group under AND").isTrue();
    }
}
