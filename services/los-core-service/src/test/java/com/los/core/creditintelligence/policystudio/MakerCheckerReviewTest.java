package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyReviewService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MakerCheckerReviewTest {

    @Test
    void authorCannotBeFinalChecker() throws Exception {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getPolicyStudio().setRequireMakerChecker(true);
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/bureau-bre/Bureau_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator(
                props, null, null, null, null, null, null, null, null, null, null, null, null, null,
                new PolicyReviewService(props));
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Bureau", "TXT", text, "alice", null);
        var rule = s.getRuleCandidates().get(0);
        orch.reviewService().review(s, "RULE", rule.getId(), "alice",
                PolicyReviewService.ROLE_POLICY_AUTHOR,
                ReviewState.CREDIT_MANAGER_APPROVED.name(), Map.of(), "authored");
        assertThatThrownBy(() -> orch.reviewService().review(s, "RULE", rule.getId(), "alice",
                PolicyReviewService.ROLE_POLICY_CHECKER,
                ReviewState.CHECKER_APPROVED.name(), Map.of(), "self check"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maker-checker");
        var ok = orch.reviewService().review(s, "RULE", rule.getId(), "bob",
                PolicyReviewService.ROLE_POLICY_CHECKER,
                ReviewState.CHECKER_APPROVED.name(), Map.of(), "checker ok");
        assertThat(ok.getReviewState()).isEqualTo(ReviewState.CHECKER_APPROVED.name());
    }
}
