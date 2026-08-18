package com.los.core.architecture.regression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN-UNDERWRITING-PLATFORM-CLOSURE-1 — generic platform guards.
 * Vikasam / Golden IDs must not leak into production code.
 */
class GoldenUnderwritingPlatformClosureGuardsTest {

    private static final String GOLDEN_APPLICATION_ID = "44f37275-1ee4-45d2-8425-a675074cb817";

    private static final List<Path> CLOSURE_PATHS = List.of(
            Path.of("src/main/java/com/los/core/creditintelligence/service/UnderwritingFactSnapshotBuilder.java"),
            Path.of("src/main/java/com/los/core/service/cam/CreditAppraisalService.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/parameters/execution/PersistedDerivedMetricSpine.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/parameters/execution/BuiltInBureauMetricProducer.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowContextFactory.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowScorecardExecutor.java"),
            Path.of("src/main/java/com/los/core/service/underwriting/PolicyWeightedScorecardEngine.java")
    );

    @Test
    void productionFixesAreGeneric() throws Exception {
        String all = readAll(CLOSURE_PATHS);
        int vikasam = countIgnoreCase(all, "vikasam");
        int goldenApp = count(all, GOLDEN_APPLICATION_ID);
        int goldenPolicy = count(all, "bf1f60a9-a236-4f0e-bfa3-64979a6ad666");
        int goldenScorecard = count(all, "17c74ade-9f84-4442-8c87-f17aad65c032");
        int equifaxRuntimeBranch = countIgnoreCase(all, "if (\"equifax\"")
                + countIgnoreCase(all, "equalsignorecase(\"equifax\")")
                + count(all, "providerCode.equalsIgnoreCase(\"EQUIFAX\")");

        assertThat(vikasam).as("VIKASAM_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        assertThat(goldenApp).as("GOLDEN_APPLICATION_ID_PRODUCTION_REFERENCE_COUNT").isZero();
        assertThat(goldenPolicy + goldenScorecard).isZero();
        assertThat(equifaxRuntimeBranch).as("EQUIFAX_SPECIFIC_POLICY_RUNTIME_BRANCH_COUNT").isZero();
    }

    @Test
    void frozenFactTriggerNotWeakened() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V86__credit_intelligence_foundation.sql"));
        assertThat(sql).contains("ci_prevent_frozen_fact_mutation");
        assertThat(sql).contains("Cannot modify facts on FROZEN snapshot");
        String builder = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/service/UnderwritingFactSnapshotBuilder.java"));
        assertThat(builder).contains("persistAndDetachFactsBeforeFreeze");
        assertThat(builder).doesNotContain("setStatus(SnapshotStatus.BUILDING.name()) // unfreeze");
        String cam = Files.readString(Path.of(
                "src/main/java/com/los/core/service/cam/CreditAppraisalService.java"));
        assertThat(cam).doesNotContain("CiUnderwritingFact");
    }

    @Test
    void persistedDerivedSpineIsExactReportScoped() throws Exception {
        String spine = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/parameters/execution/PersistedDerivedMetricSpine.java"));
        assertThat(spine).contains("findByBureauReportId");
        assertThat(spine).doesNotContain("findByApplicationIdOrderByCreatedAtDesc");
        assertThat(spine).doesNotContain("findFirstByApplicationIdOrderByCreatedAtDesc");
        String factory = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowContextFactory.java"));
        assertThat(factory).contains("persistedDerivedMetricSpine.bindExactReport");
        String producer = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/parameters/execution/BuiltInBureauMetricProducer.java"));
        assertThat(producer).contains("PersistedDerivedMetricSpine.PRECOMPUTED_METRICS");
        assertThat(producer).doesNotContain("recalculate");
    }

    @Test
    void scorecardExecutorDoesNotSkipConfiguredFactors() throws Exception {
        String exec = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowScorecardExecutor.java"));
        assertThat(exec).contains("PolicyWeightedScorecardEngine");
        assertThat(exec).contains("CONDITION_PRESENT");
        assertThat(exec).contains("configuredExecutableFactorSkippedCount");
        assertThat(exec).doesNotContain("if (!ScorecardExclusiveBandModel.MODE_EXCLUSIVE.equals(factor.mode())) {\n                continue;");
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

    private static int countIgnoreCase(String src, String token) {
        return count(src.toLowerCase(Locale.ROOT), token.toLowerCase(Locale.ROOT));
    }
}
