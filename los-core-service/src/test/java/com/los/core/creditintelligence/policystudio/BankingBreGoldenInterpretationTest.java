package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.policystudio.domain.AmbiguityType;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankingBreGoldenInterpretationTest {

    private PolicyStudioSession session;

    @BeforeEach
    void setUp() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/banking-bre/Banking_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        session = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Banking_BRE", "TXT", text, "test", "Banking_BRE.txt");
    }

    @Test
    void extractsFieldsRequiredAsInformationRequirement() {
        assertThat(session.getClauses()).anyMatch(c ->
                "Fields Required in Excel Report".equals(c.getSection())
                        && ClauseType.INFORMATION_REQUIREMENT.name().equals(c.getClauseType()));
        assertThat(session.getClauses().stream()
                .filter(c -> "Fields Required in Excel Report".equals(c.getSection())).count())
                .isGreaterThanOrEqualTo(8);
    }

    @Test
    void productScopesAreDistinct() {
        assertThat(session.getClauses()).anyMatch(c -> "STARTER".equals(c.getProductScope()));
        assertThat(session.getClauses()).anyMatch(c -> "DIGILEAP".equals(c.getProductScope()));
        assertThat(session.getClauses()).anyMatch(c -> "SMART_SWITCH".equals(c.getProductScope()));
        assertThat(session.getClauses()).anyMatch(c -> "REBOOST".equals(c.getProductScope()));
    }

    @Test
    void metricAdjustmentsClassifiedSeparately() {
        assertThat(session.getClauses().stream()
                .filter(c -> ClauseType.METRIC_ADJUSTMENT.name().equals(c.getClauseType())).count())
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void ambiguitiesIncludeEdiBoundaryAndDepositions() {
        assertThat(session.getAmbiguities()).anyMatch(a ->
                AmbiguityType.UNKNOWN_BUSINESS_TERM.name().equals(a.getAmbiguityType())
                        && "EDI".equalsIgnoreCase(a.getPhrase()));
        assertThat(session.getAmbiguities()).anyMatch(a ->
                AmbiguityType.BOUNDARY_AMBIGUITY.name().equals(a.getAmbiguityType()));
        assertThat(session.getAmbiguities()).anyMatch(a ->
                a.getPhrase() != null && a.getPhrase().toLowerCase().contains("deposition"));
        assertThat(session.getAmbiguities()).anyMatch(a ->
                AmbiguityType.MULTIPLE_CANONICAL_MATCHES.name().equals(a.getAmbiguityType()));
        assertThat(session.getAmbiguities()).anyMatch(a ->
                AmbiguityType.MISSING_METRIC.name().equals(a.getAmbiguityType()));
    }

    @Test
    void metricCandidateBankPolicyAdjustedAdb() {
        assertThat(session.getMetricCandidates()).anyMatch(m ->
                "BANK_POLICY_ADJUSTED_ADB".equals(m.getCandidateCanonicalCode())
                        || "BANK_POLICY_ADJUSTED_ADB".equals(m.getSystemMetricId()));
        assertThat(session.getMetricCandidates().stream()
                .filter(m -> "BANK_POLICY_ADJUSTED_ADB".equals(m.getSystemMetricId()))
                .findFirst().orElseThrow().getExclusions()).isNotEmpty();
    }

    @Test
    void ruleCandidatesHaveSystemIdsAndDsl() {
        assertThat(session.getRuleCandidates()).anyMatch(r -> "BANK_STARTER_ADB_GTE_EDI".equals(r.getSystemRuleId()));
        assertThat(session.getRuleCandidates()).anyMatch(r -> "BANK_DIGILEAP_ADB_DIV5_GTE_EDI".equals(r.getSystemRuleId()));
        assertThat(session.getRuleCandidates()).anyMatch(r -> "BANK_DIGILEAP_TXN_GTE_20".equals(r.getSystemRuleId()));
        assertThat(session.getRuleCandidates()).anyMatch(r ->
                r.getSystemRuleId().startsWith("BANK_SMART_SWITCH"));
        assertThat(session.getRuleCandidates()).anyMatch(r ->
                r.getSystemRuleId().contains("REBOOST") && r.getSystemRuleId().contains("60000"));
        assertThat(session.getRuleCandidates()).anyMatch(r -> "BANK_INWARD_RETURN_BRANCHED_100".equals(r.getSystemRuleId()));
        var starter = session.getRuleCandidates().stream()
                .filter(r -> "BANK_STARTER_ADB_GTE_EDI".equals(r.getSystemRuleId())).findFirst().orElseThrow();
        assertThat(starter.getExpression()).containsKey("op");
        assertThat(starter.getOnMissing()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(starter.getLineage()).containsKeys("clauseId", "sourceText");
    }

    @Test
    void reboostUsesGtNotGteFor60000() {
        var reboost = session.getRuleCandidates().stream()
                .filter(r -> r.getSystemRuleId().contains("REBOOST") && r.getSystemRuleId().contains("ADB"))
                .findFirst().orElseThrow();
        assertThat(reboost.getExpression().toString()).contains("GT");
        assertThat(reboost.getScope().toString()).contains("60000");
    }
}
