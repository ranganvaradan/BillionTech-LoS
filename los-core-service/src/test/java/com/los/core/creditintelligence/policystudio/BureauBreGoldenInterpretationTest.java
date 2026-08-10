package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.domain.AmbiguityType;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BureauBreGoldenInterpretationTest {

    private PolicyStudioSession session;

    @BeforeEach
    void setUp() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/bureau-bre/Bureau_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        session = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Bureau_BRE", "TXT", text, "test", "Bureau_BRE.txt");
    }

    @Test
    void nestedOverdueParentWithFourChildren() {
        var parent = session.getClauses().stream()
                .filter(c -> "OVERDUE_PARENT".equals(c.getClauseNumber()))
                .findFirst().orElseThrow();
        var children = session.getClauses().stream()
                .filter(c -> parent.getId().equals(c.getParentClauseId()))
                .toList();
        assertThat(children).hasSize(4);
        assertThat(children).extracting(c -> c.getClauseNumber()).containsExactly("1", "2", "3", "4");
    }

    @Test
    void scoreUsesOrSemanticsNotSimplyGte650() {
        var score = session.getRuleCandidates().stream()
                .filter(r -> "BUREAU_SCORE_OR_NTC_OR_GTE_650".equals(r.getSystemRuleId()))
                .findFirst().orElseThrow();
        assertThat(score.getExpression().get("op")).isEqualTo("OR");
        assertThat(score.getExpression().toString()).contains("-1");
        assertThat(score.getExpression().toString()).contains("650");
        assertThat(score.getExpression().toString()).containsIgnoringCase("ntc");
    }

    @Test
    void ambiguitiesForNtcCleanDbtPwosLssAndMissingMetrics() {
        assertThat(session.getAmbiguities()).anyMatch(a ->
                "NTC".equalsIgnoreCase(a.getPhrase()));
        assertThat(session.getAmbiguities()).anyMatch(a ->
                a.getPhrase() != null && a.getPhrase().toUpperCase().contains("CLEAN"));
        assertThat(session.getAmbiguities()).anyMatch(a -> "DBT".equalsIgnoreCase(a.getPhrase()));
        assertThat(session.getAmbiguities()).anyMatch(a -> "PWOS".equalsIgnoreCase(a.getPhrase()));
        assertThat(session.getAmbiguities()).anyMatch(a -> "LSS".equalsIgnoreCase(a.getPhrase()));
        assertThat(session.getAmbiguities().stream()
                .filter(a -> AmbiguityType.MISSING_METRIC.name().equals(a.getAmbiguityType())).count())
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void metricCandidatesForOverdueChildren() {
        assertThat(session.getMetricCandidates()).anyMatch(m ->
                "bureau.overdue.age_months".equals(m.getCandidateCanonicalCode()));
        assertThat(session.getMetricCandidates()).anyMatch(m ->
                "bureau.credit_after_overdue.exists".equals(m.getCandidateCanonicalCode()));
        assertThat(session.getMetricCandidates()).anyMatch(m ->
                "bureau.credit_after_overdue.clean_history_months".equals(m.getCandidateCanonicalCode()));
    }

    @Test
    void coreBureauRulesPresent() {
        assertThat(session.getRuleCandidates()).extracting(r -> r.getSystemRuleId())
                .contains(
                        "BUREAU_DPD_LAST_6M",
                        "BUREAU_INQUIRIES_CURRENT_MONTH",
                        "BUREAU_CC_OVERDUE_GT_5000",
                        "BUREAU_SETTLED_OR_RESTRUCTURED",
                        "BUREAU_LEGAL_SUIT",
                        "BUREAU_DBT_PWOS_LSS",
                        "BUREAU_MULTIPLE_PAN",
                        "BUREAU_ACCOUNT_SOLD",
                        "BUREAU_NO_WRITEOFF_EXCEPT_CC"
                );
    }

    @Test
    void generatedBoundaryTestsForDpdInquiryCc() {
        assertThat(session.getTestCases()).anyMatch(t -> "DPD_30_PASS".equals(t.getName()));
        assertThat(session.getTestCases()).anyMatch(t -> "DPD_31_FAIL".equals(t.getName()));
        assertThat(session.getTestCases()).anyMatch(t -> "INQ_3_PASS".equals(t.getName()));
        assertThat(session.getTestCases()).anyMatch(t -> "INQ_4_FAIL".equals(t.getName()));
        assertThat(session.getTestCases()).anyMatch(t -> "CC_OVERDUE_5000_PASS".equals(t.getName()));
        assertThat(session.getTestCases()).anyMatch(t -> "CC_OVERDUE_5001_FAIL".equals(t.getName()));
        var inq = session.getInterpretations().stream()
                .filter(i -> i.getCandidatePeriod() != null
                        && i.getCandidatePeriod().contains("CURRENT_MONTH"))
                .findFirst();
        assertThat(inq).isPresent();
    }
}
