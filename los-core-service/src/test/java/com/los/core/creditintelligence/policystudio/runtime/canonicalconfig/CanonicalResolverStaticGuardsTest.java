package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static regression guards for the canonical resolver package.
 * Forbidden: latest lookup, product default, wall-clock as-of, silent unpinned RESOLVED packages.
 */
class CanonicalResolverStaticGuardsTest {

    @Test
    void canonicalResolverHasZeroForbiddenLookups() throws Exception {
        Path resolver = Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalconfig/CanonicalApplicationConfigurationResolver.java");
        assertThat(resolver).exists();
        String all = Files.readString(resolver);
        String lower = all.toLowerCase(Locale.ROOT);

        int latestLookup = count(all, "latestFor(")
                + count(all, "findFirstByCodeOrderByVersionNoDesc")
                + count(all, "findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc")
                + count(all, "findFirstByApplicationIdOrderByCreatedAtDesc")
                + count(all, "findByApplicationIdOrderByCreatedAtDesc")
                + count(all, "discoverDefault");
        int productDefault = count(all, "findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc")
                + count(lower, "product default")
                + count(all, "underwriting_rule_sets");
        int wallClock = count(all, "LocalDate.now(");

        assertThat(latestLookup)
                .as("CANONICAL_RESOLVER_LATEST_LOOKUP_COUNT")
                .isZero();
        assertThat(productDefault)
                .as("CANONICAL_RESOLVER_PRODUCT_DEFAULT_COUNT")
                .isZero();
        assertThat(wallClock)
                .as("CANONICAL_RESOLVER_WALL_CLOCK_FALLBACK_COUNT")
                .isZero();
        assertThat(all).doesNotContain("CanonicalPolicyRuntime");
        assertThat(all).contains("CUSTOMER_CATEGORY_NOT_PINNED");
        assertThat(all).contains("WORKFLOW_VERSION_NOT_PINNED");
        assertThat(all).contains("wall-clock now forbidden");
        String app = Files.readString(Path.of(
                "src/main/java/com/los/core/LosCoreServiceApplication.java"));
        assertThat(app).contains("com.los.core.creditintelligence.policystudio.runtime.canonicalconfig");
    }

    @Test
    void liveDecisionPathDoesNotUseCanonicalPolicyRuntime() throws Exception {
        String flow = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        assertThat(flow).doesNotContain("CanonicalPolicyRuntime");
        assertThat(flow).doesNotContain("CanonicalApplicationConfigurationResolver");
        String foundation = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/service/CreditIntelligenceFoundationService.java"));
        assertThat(foundation).contains("canonicalApplicationConfigurationFreezeService.freezeObservably");
        assertThat(foundation).contains("Canonical application configuration freeze ignored");
        String engine = Files.readString(Path.of(
                "src/main/java/com/los/core/service/underwriting/UnderwritingRuleEngine.java"));
        String scorecard = Files.readString(Path.of(
                "src/main/java/com/los/core/service/underwriting/ScorecardPolicyEngine.java"));
        assertThat(engine).doesNotContain("CanonicalApplicationConfigurationResolver");
        assertThat(scorecard).doesNotContain("CanonicalApplicationConfigurationResolver");
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
