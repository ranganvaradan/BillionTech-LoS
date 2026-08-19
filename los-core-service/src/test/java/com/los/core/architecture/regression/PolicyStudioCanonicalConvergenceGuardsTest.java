package com.los.core.architecture.regression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-STUDIO-CANONICAL-CONVERGENCE-1 — generic Policy Studio Test guards.
 */
class PolicyStudioCanonicalConvergenceGuardsTest {

    private static final List<Path> PATHS = List.of(
            Path.of("src/main/java/com/los/core/creditintelligence/staging/PolicyStudioTestExperienceService.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalObservationalEvaluationService.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalObservationalEvaluation.java")
    );

    @Test
    void productionPolicyTestHasNoGoldenOrEquifaxBranches() throws Exception {
        String all = readAll(PATHS);
        assertThat(countIgnoreCase(all, "vikasam")).as("VIKASAM_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        assertThat(count(all, "67d296d1-d570-4259-9e8a-e82287b10af3"))
                .as("GOLDEN_APPLICATION_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        assertThat(count(all, "44f37275-1ee4-45d2-8425-a675074cb817")).isZero();
        assertThat(count(all, "b1ccb0f4-ea04-425b-a20f-1af8953a1c10"))
                .as("GOLDEN_POLICY_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        assertThat(count(all, "17c74ade-9f84-4442-8c87-f17aad65c032")).isZero();
        int equifax = countIgnoreCase(all, "if (\"equifax\"")
                + countIgnoreCase(all, "equalsignorecase(\"equifax\")")
                + count(all, "providerCode.equalsIgnoreCase(\"EQUIFAX\")");
        assertThat(equifax).as("EQUIFAX_SPECIFIC_POLICY_TEST_BRANCH_COUNT").isZero();
        assertThat(all).contains("CanonicalObservationalEvaluationService");
        assertThat(all).doesNotContain("findFirstByCanonicalParameterIdOrderByVersionDesc");
        assertThat(all).doesNotContain("LocalDate.now(");
    }

    @Test
    void liveFlowUsesCanonicalLiveServiceWhenCutoverEnabled() throws Exception {
        String flow = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        assertThat(flow).contains("CanonicalLiveUnderwritingService");
        assertThat(flow).contains("underwriteWithCanonicalAuthority");
        assertThat(flow).doesNotContain("new CanonicalPolicyRuntime");
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
