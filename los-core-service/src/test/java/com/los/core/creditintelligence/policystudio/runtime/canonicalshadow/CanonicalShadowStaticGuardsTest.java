package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W11.3 static guards: canonical shadow must not look up latest artifacts or wall-clock as-of.
 */
class CanonicalShadowStaticGuardsTest {

    private static final List<Path> SHADOW_PATH = List.of(
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowUnderwritingService.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowContextFactory.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowScorecardExecutor.java")
    );

    @Test
    void shadowPathHasZeroLatestAndWallClockFallbacks() throws Exception {
        String all = readAll(SHADOW_PATH);
        int latestWorkflow = count(all, "findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc")
                + count(all, "latestFor(");
        int latestPolicy = count(all, "findFirstByPolicyDocumentIdOrderByDocumentVersionDesc")
                + count(all, "latestGraph(");
        int latestScorecard = count(all, "findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc");
        int latestBureau = count(all, "findFirstByApplicationIdOrderByCreatedAtDesc")
                + count(all, "findByApplicationIdOrderByCreatedAtDesc");
        int wallClock = count(all, "LocalDate.now(");

        assertThat(latestWorkflow).as("SHADOW_LATEST_WORKFLOW_LOOKUP_COUNT").isZero();
        assertThat(latestPolicy).as("SHADOW_LATEST_POLICY_LOOKUP_COUNT").isZero();
        assertThat(latestScorecard).as("SHADOW_LATEST_SCORECARD_LOOKUP_COUNT").isZero();
        // forbidLatestFor is required; latest calc lookup must stay zero excluding that token
        assertThat(count(all, "findFirstByCanonicalParameterIdOrderByVersionDesc"))
                .as("SHADOW_LATEST_CALC_DEF_LOOKUP_COUNT").isZero();
        assertThat(all).contains("forbidLatestFor");
        assertThat(latestBureau).as("SHADOW_LATEST_BUREAU_REPORT_LOOKUP_COUNT").isZero();
        assertThat(wallClock).as("SHADOW_WALL_CLOCK_ASOF_FALLBACK_COUNT").isZero();
        assertThat(all).doesNotContain("import com.los.core.creditintelligence.service.ShadowCreditEvaluationService");
        assertThat(all).doesNotContain("shadowCreditEvaluationService");
        assertThat(all).doesNotContain("import com.los.core.service.credit.CreditDecisionServiceImpl");
        assertThat(all).doesNotContain("creditDecisionService");
        assertThat(all).doesNotContain("import com.los.core.service.underwriting.ScorecardPolicyEngine");
        assertThat(all).doesNotContain("ScorecardPolicyEngine.evaluate");
        assertThat(all).doesNotContain("underwriting_rule_sets");
        assertThat(all.toLowerCase(Locale.ROOT)).doesNotContain("canonical_live");
    }

    @Test
    void liveDecisionPathStillDoesNotUseCanonicalRuntime() throws Exception {
        String flow = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        assertThat(flow).doesNotContain("CanonicalPolicyRuntime");
        assertThat(flow).doesNotContain("CanonicalShadowUnderwritingService");
        assertThat(flow).doesNotContain("CanonicalApplicationConfigurationResolver");
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains("CREDIT_INTELLIGENCE_CANONICAL_SHADOW_MODE:LEGACY_ONLY");
        String staging = Files.readString(Path.of("src/main/resources/application-staging.yml"));
        assertThat(staging).doesNotContain("LEGACY_WITH_CANONICAL_SHADOW");
        String app = Files.readString(Path.of("src/main/java/com/los/core/LosCoreServiceApplication.java"));
        assertThat(app).contains("com.los.core.creditintelligence.policystudio.runtime.canonicalshadow");
        String foundation = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/service/CreditIntelligenceFoundationService.java"));
        assertThat(foundation).contains("dispatchCanonicalShadow");
        assertThat(foundation).contains("canonicalShadowUnderwritingService.afterLiveDecision");
    }

    private static String readAll(List<Path> paths) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Path p : paths) {
            assertThat(p).exists();
            sb.append(Files.readString(p)).append('\n');
        }
        return sb.toString();
    }

    private static int count(String src, String token) {
        int n = 0;
        int from = 0;
        while (true) {
            int i = src.indexOf(token, from);
            if (i < 0) {
                return n;
            }
            n++;
            from = i + token.length();
        }
    }
}
