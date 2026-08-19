package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.runtime.canonicallive.CanonicalLiveUnderwritingService;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalObservationalEvaluationService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W11.4 — canonical live authority cutover guards.
 */
class CanonicalLiveAuthorityCutoverGuardsTest {

    private static final List<Path> PRODUCTION_PATHS = List.of(
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicallive/CanonicalLiveUnderwritingService.java"),
            Path.of("src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"),
            Path.of("src/main/java/com/los/core/service/underwriting/UnderwritingEvaluationService.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalObservationalEvaluationService.java"),
            Path.of("src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowUnderwritingService.java")
    );

    @Test
    void productionHasNoGoldenOrClientSpecificBranches() throws Exception {
        String all = readAll(PRODUCTION_PATHS);
        assertThat(countIgnoreCase(all, "vikasam")).as("VIKASAM_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        assertThat(count(all, "67d296d1-d570-4259-9e8a-e82287b10af3"))
                .as("GOLDEN_APPLICATION_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        assertThat(count(all, "b1ccb0f4-ea04-425b-a20f-1af8953a1c10"))
                .as("GOLDEN_POLICY_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        assertThat(count(all, "17c74ade-9f84-4442-8c87-f17aad65c032"))
                .as("GOLDEN_SCORECARD_SPECIFIC_PRODUCTION_CODE_COUNT").isZero();
        int equifax = countIgnoreCase(all, "if (\"equifax\"")
                + count(all, "providerCode.equalsIgnoreCase(\"EQUIFAX\")");
        assertThat(equifax).as("EQUIFAX_SPECIFIC_POLICY_RUNTIME_BRANCH_COUNT").isZero();
        assertThat(countIgnoreCase(all, "account_sold"))
                .as("ACCOUNT_SOLD_SPECIFIC_RUNTIME_BRANCH_COUNT").isZero();
    }

    @Test
    void liveFlowUsesObservationalServiceNotSecondRuntime() throws Exception {
        String flow = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        String live = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicallive/CanonicalLiveUnderwritingService.java"));
        assertThat(flow).contains("CanonicalLiveUnderwritingService");
        assertThat(flow).contains("underwriteWithCanonicalAuthority");
        assertThat(live).contains("CanonicalObservationalEvaluationService");
        assertThat(live).doesNotContain("new CanonicalPolicyRuntime");
        assertThat(live).doesNotContain("UnderwritingRuleEngine");
        assertThat(live).doesNotContain("ScorecardPolicyEngine");
        assertThat(live).doesNotContain("CreditDecisionServiceImpl");
    }

    @Test
    void legacyEnginesNotFallbackWhenCutoverBranchPresent() throws Exception {
        String flow = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        int cutoverIdx = flow.indexOf("underwriteWithCanonicalAuthority");
        int legacyIdx = flow.indexOf("underwritingRuleEngine.evaluateAll");
        assertThat(cutoverIdx).isGreaterThan(0);
        assertThat(legacyIdx).isGreaterThan(cutoverIdx);
        assertThat(flow).contains("isAuthoritativeFor(app)");
    }

    @Test
    void shadowSkipsWhenCanonicalLivePersisted() throws Exception {
        String shadow = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalShadowUnderwritingService.java"));
        assertThat(shadow).contains("isCanonicalLiveProduction");
        assertThat(shadow).contains(CanonicalLiveUnderwritingService.PRODUCTION_AUTHORITY);
    }

    @Test
    void observationalServiceSharedWithPolicyStudioTest() throws Exception {
        String obs = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/runtime/canonicalshadow/CanonicalObservationalEvaluationService.java"));
        assertThat(obs).contains(CanonicalObservationalEvaluationService.SERVICE);
        assertThat(obs).doesNotContain("findFirstByCanonicalParameterIdOrderByVersionDesc");
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
