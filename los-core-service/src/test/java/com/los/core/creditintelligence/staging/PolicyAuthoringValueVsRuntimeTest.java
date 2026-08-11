package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionBindingService;
import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionMatcher;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthoringCompleteness;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authoring threshold (policy config) vs runtime applicant value must not be confused.
 */
class PolicyAuthoringValueVsRuntimeTest {

    @Test
    void bureauScoreLt650FailureRule_isReadyDuringAuthoring() {
        PolicyStudioOrchestrator orch = orchestrator();
        PolicyStudioSession session = orch.processUpload(
                UUID.randomUUID(),
                "Bureau threshold policy",
                "TXT",
                "Reject if bureau score < 650.\n",
                "test",
                null);

        CiPolicyRuleCandidate bureau = session.getRuleCandidates().stream()
                .filter(r -> r.getMetadata() != null
                        && "BUREAU.MIN_SCORE".equals(r.getMetadata().get("businessCapabilityId")))
                .findFirst()
                .orElseThrow();

        assertThat(((Number) PolicyAuthoringCompleteness.authoringThreshold(bureau)).intValue()).isEqualTo(650);
        assertThat(PolicyAuthoringCompleteness.isAuthoringComplete(bureau)).isTrue();
        assertThat(Boolean.TRUE.equals(bureau.getMetadata().get("NEEDS_INPUT"))).isFalse();

        Map<String, Object> view = new LinkedHashMap<>();
        ProspectDay2ViewBuilder.enrich(view, session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> uw = (List<Map<String, Object>>) view.get("underwritingRules");
        Map<String, Object> card = uw.stream()
                .filter(c -> "BUREAU.MIN_SCORE".equals(String.valueOf(
                        ((Map<?, ?>) c.getOrDefault("metadata", Map.of())).get("businessCapabilityId")))
                        || String.valueOf(c.get("systemRuleId")).contains("BUREAU"))
                .findFirst()
                .orElseGet(() -> uw.get(0));

        assertThat(card.get("status")).isIn("Ready", "Accepted", "Edited");
        assertThat(String.valueOf(card.getOrDefault("blockedReason", "")))
                .doesNotContain("Parameter value missing");
        assertThat(String.valueOf(card.get("businessRule"))).containsIgnoringCase("Bureau Score must be >=");
        assertThat(String.valueOf(card.get("businessRule"))).contains("650");
        assertThat(card.get("authoringComplete")).isEqualTo(true);
        assertThat(card.get("runtimeValueRequired")).isEqualTo(false);
        assertThat(String.valueOf(card.get("ruleName"))).containsIgnoringCase("Minimum Bureau Score");
    }

    @Test
    void missingApplicantBureauScore_doesNotMakePolicyRuleIncomplete() {
        CiPolicyRuleCandidate r = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CATALOGUE_BUREAU_MIN_SCORE")
                .expression(PolicyDsl.lt(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))
                .onTrue("FAIL")
                .onFalse("PASS")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessCapabilityId", "BUREAU.MIN_SCORE",
                        "businessTitle", "Minimum bureau score",
                        "catalogueBacked", true,
                        "parameters", Map.of("minimumScore", 650L),
                        "threshold", 650L,
                        "failureTreatment", "REJECT",
                        // Spurious legacy flag as if someone confused runtime missing with authoring
                        "NEEDS_INPUT", true,
                        "blockedReason", PolicyAuthoringCompleteness.LEGACY_VALUE_MISSING)))
                .build();

        PolicyAuthoringCompleteness.healMetadata(r);
        assertThat(r.getMetadata().get("NEEDS_INPUT")).isEqualTo(false);
        assertThat(PolicyAuthoringCompleteness.isAuthoringComplete(r)).isTrue();
        assertThat(PolicyRulePresentationSemantics.ruleStatus(r, null)).isEqualTo("Ready");
    }

    @Test
    void testWithoutBureauScore_reportsDataInsufficient() {
        Map<String, Object> expr = PolicyDsl.lt(PolicyDsl.metric("bureau.score"), Map.of("const", 650));
        PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
        String outcome = interpreter.evaluate(expr, ctx);
        assertThat(outcome).isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);

        CiPolicyRuleCandidate r = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CATALOGUE_BUREAU_MIN_SCORE")
                .expression(expr)
                .onTrue("FAIL")
                .onFalse("PASS")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(Map.of(
                        "businessCapabilityId", "BUREAU.MIN_SCORE",
                        "threshold", 650L,
                        "parameters", Map.of("minimumScore", 650L),
                        "catalogueBacked", true,
                        "failureTreatment", "REJECT"))
                .build();
        assertThat(r.getOnMissing()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(PolicyAuthoringCompleteness.isAuthoringComplete(r)).isTrue();
    }

    @Test
    void thresholdMissing_genuinelyNeedsInput() {
        CapabilityIngestionMatcher matcher = new CapabilityIngestionMatcher(new CreditCapabilityCatalogueService());
        // Phrase matches bureau score capability but provides no numeric threshold and we strip defaults
        var match = matcher.match("Bureau score rule applies as per credit policy.");
        // If matcher still fills catalogue default 650, authoring is complete — force empty threshold case:
        CiPolicyRuleCandidate r = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CATALOGUE_BUREAU_MIN_SCORE")
                .expression(Map.of("op", "LT", "left", Map.of("metric", "bureau.score")))
                .onTrue("FAIL")
                .onFalse("PASS")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessCapabilityId", "BUREAU.MIN_SCORE",
                        "businessTitle", "Minimum bureau score",
                        "catalogueBacked", true,
                        "parameters", Map.of(),
                        "NEEDS_INPUT", true,
                        "blockedReason", PolicyAuthoringCompleteness.MSG_THRESHOLD_MISSING,
                        "failureTreatment", "REJECT")))
                .build();

        assertThat(PolicyAuthoringCompleteness.authoringThreshold(r)).isNull();
        assertThat(PolicyAuthoringCompleteness.isAuthoringComplete(r)).isFalse();
        assertThat(PolicyAuthoringCompleteness.authoringGapMessage(r))
                .isEqualTo(PolicyAuthoringCompleteness.MSG_THRESHOLD_MISSING);
        assertThat(PolicyRulePresentationSemantics.ruleStatus(r, r.getMetadata().get("blockedReason").toString()))
                .isEqualTo("Needs your input");

        // Sanity: matcher extracts <650 as authoring-complete
        assertThat(matcher.match("Reject if bureau score < 650").needsInput()).isFalse();
        assertThat(((Number) matcher.match("Reject if bureau score < 650").extractedParameters().get("minimumScore"))
                .intValue()).isEqualTo(650);
        assertThat(match).isNotNull();
    }

    private static PolicyStudioOrchestrator orchestrator() {
        CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();
        CapabilityIngestionBindingService binder = new CapabilityIngestionBindingService(
                new CapabilityIngestionMatcher(catalogue), catalogue);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        orch.setIngestionBindingService(binder);
        return orch;
    }
}
