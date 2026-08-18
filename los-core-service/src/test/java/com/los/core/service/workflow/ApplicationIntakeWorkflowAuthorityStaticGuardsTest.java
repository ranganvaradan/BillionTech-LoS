package com.los.core.service.workflow;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * After Customer Category pins a Workflow Version, application orchestration must not
 * discover Default BTL, product-latest, or latest-active workflow.
 */
class ApplicationIntakeWorkflowAuthorityStaticGuardsTest {

    private static final List<Path> APPLICATION_PATH = List.of(
            Path.of("src/main/java/com/los/core/service/workflow/ApplicationWorkflowResolver.java"),
            Path.of("src/main/java/com/los/core/service/workflow/ActiveWorkflowConfigService.java"),
            Path.of("src/main/java/com/los/core/service/workflow/ApplicationConfigurationAuthority.java"),
            Path.of("src/main/java/com/los/core/service/workflow/intake/WorkflowIntakeValidator.java"),
            Path.of("src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"),
            Path.of("src/main/java/com/los/core/service/loan/LoanApplicationServiceImpl.java")
    );

    @Test
    void applicationPathDoesNotDiscoverDefaultOrLatestWorkflow() throws Exception {
        String all = readAll(APPLICATION_PATH).toLowerCase(Locale.ROOT);
        assertThat(count(all, "discoverdefault")).as("DEFAULT_WORKFLOW_LOOKUP_COUNT").isZero();
        assertThat(count(all, "workflowresolutionsource.default")).as("DEFAULT_SOURCE_COUNT").isZero();
        assertThat(count(all, "findbyborrowertypeandloanproductandintakesegmentandactivetrueorderbyversiondesc"))
                .as("LATEST_WORKFLOW_LOOKUP_COUNT")
                .isZero();
        assertThat(count(all, "getactiveworkflow(")).as("PRODUCT_WORKFLOW_OVERRIDE_COUNT").isZero();
        assertThat(all).doesNotContain("vikasam");
        assertThat(all).doesNotContain("golden");
    }

    @Test
    void submitConsumesPinnedWorkflowOnly() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        assertThat(src).contains("assertReadyForSubmit");
        assertThat(src).contains("findActiveForApplication");
        assertThat(src).contains("workflowIntakeValidator.validateAtSubmit");
        assertThat(src).contains("KYC_IN_PROGRESS");
        assertThat(src).doesNotContain("getActiveWorkflow(");
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
