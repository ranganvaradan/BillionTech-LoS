package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionMatcher;
import com.los.core.creditintelligence.policystudio.catalogue.CatalogueCapabilityExpressionBuilder;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.catalogue.EnquiryWindowSpec;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslSemanticEquivalence;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUREAU-CERTIFICATION-P0-CLOSURE-2 — compound AST preserve, enquiry windows, single NTC authority.
 */
class BureauCertificationP0Closure2Test {

    private static final Map<String, Object> COMPOUND = PolicyDsl.or(
            PolicyDsl.eq(PolicyDsl.metric("bureau.score"), -1),
            PolicyDsl.eq(PolicyDsl.fact("bureau.status_ntc"), true),
            PolicyDsl.gte(PolicyDsl.metric("bureau.score"), 650));

    private final CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();

    @Test
    void p0a_compoundAstSurvivesCatalogueMatch_andBoundaries() throws Exception {
        String text = new String(
                getClass().getResourceAsStream("/policy-fixtures/bureau-bre/Bureau_BRE.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        PolicyStudioSession s = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "Bureau_BRE", "TXT", text, "test", "Bureau_BRE.txt");
        var score = s.getRuleCandidates().stream()
                .filter(r -> "BUREAU_SCORE_OR_NTC_OR_GTE_650".equals(r.getSystemRuleId()))
                .findFirst().orElseThrow();
        assertThat(score.getExpression().get("op")).isEqualTo("OR");
        assertThat(Boolean.TRUE.equals(score.getMetadata().get("catalogueAstSuppressed"))).isTrue();

        PolicyDslInterpreterV1 interp = new PolicyDslInterpreterV1();
        assertDecision(interp, Map.of("bureau.score", -1), Map.of("bureau.status_ntc", false), true);
        assertDecision(interp, Map.of("bureau.score", 0), Map.of("bureau.status_ntc", true), true);
        assertDecision(interp, Map.of("bureau.score", 649), Map.of("bureau.status_ntc", false), false);
        assertDecision(interp, Map.of("bureau.score", 650), Map.of("bureau.status_ntc", false), true);
        assertDecision(interp, Map.of("bureau.score", 651), Map.of("bureau.status_ntc", false), true);
    }

    @Test
    void p0a_simpleMinScore_bindsToCatalogueEquivalence() {
        Map<String, Object> authored = PolicyDsl.gte(PolicyDsl.metric("bureau.score"), 650);
        var cap = catalogue.findById("BUREAU.MIN_SCORE").orElseThrow();
        var built = CatalogueCapabilityExpressionBuilder.build(cap, Map.of("minimumScore", 650L), "REJECT");
        assertThat(PolicyDslSemanticEquivalence.equivalent(
                authored, "PASS", "FAIL",
                built.expression(), built.onTrue(), built.onFalse())).isTrue();

        PolicyStudioSession s = new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), "SimpleScore", "TXT",
                "Bureau score must be at least 650.", "test", "simple.txt");
        assertThat(s.getRuleCandidates()).anyMatch(r ->
                r.getExpression() != null
                        && String.valueOf(r.getExpression()).contains("bureau.score")
                        && !"OR".equals(String.valueOf(r.getExpression().get("op")))
                        && !Boolean.TRUE.equals(r.getMetadata() == null
                        ? null : r.getMetadata().get("catalogueAstSuppressed")));
    }

    @Test
    void p0b_enquiryWindows_distinct() {
        CapabilityIngestionMatcher matcher = new CapabilityIngestionMatcher(catalogue);
        var cap = catalogue.findById("BUREAU.ENQUIRIES_MAX").orElseThrow();

        var cur = matcher.match("Bureau enquiries in current month should be <= 3");
        assertThat(cur.extractedParameters().get("windowKind")).isEqualTo(EnquiryWindowSpec.CURRENT_MONTH);
        var builtCur = CatalogueCapabilityExpressionBuilder.build(cap, cur.extractedParameters(), "REJECT");
        assertThat(builtCur.metricPath()).isEqualTo("bureau.inquiries.current_month");

        var m3 = matcher.match("Bureau enquiries in last 3 months should be <= 5");
        assertThat(m3.extractedParameters().get("windowKind")).isEqualTo(EnquiryWindowSpec.LAST_3_MONTHS);
        var built3 = CatalogueCapabilityExpressionBuilder.build(cap, m3.extractedParameters(), "REJECT");
        assertThat(built3.metricPath()).isEqualTo("bureau.inquiries.last_3m");

        var d90 = matcher.match("Bureau enquiries in last 90 days should be <= 5");
        assertThat(d90.extractedParameters().get("windowKind")).isEqualTo(EnquiryWindowSpec.LAST_90_DAYS);
        var built90 = CatalogueCapabilityExpressionBuilder.build(cap, d90.extractedParameters(), "REJECT");
        assertThat(built90.metricPath()).isEqualTo("bureau.recent_inquiries_90d");

        assertThat(CapabilityIngestionMatcher.extractEnquiryWindow(
                "more than three inquiries in the current month",
                "more than three inquiries in the current month").kind())
                .isEqualTo(EnquiryWindowSpec.CURRENT_MONTH);
    }

    @Test
    void p0c_studioDelegatesToSharedNtcAuthority() {
        BureauMetricService live = new BureauMetricService(null, null);
        PolicyBureauMetricService studio = new PolicyBureauMetricService();

        var liveHit = live.evaluateStatusNtc(720, null, false, null);
        var studioHit = studio.consumerNtc(720, null, null, false);
        assertThat(liveHit.value01()).isEqualTo(0);
        assertThat(studioHit.get("value")).isEqualTo(false);

        var liveNoHit = live.evaluateStatusNtc(-1, null, true, null);
        var studioNoHit = studio.consumerNtc(-1, null, null, true);
        assertThat(liveNoHit.value01()).isEqualTo(1);
        assertThat(studioNoHit.get("value")).isEqualTo(true);

        var liveMinusOne = live.evaluateStatusNtc(-1, null, false, null);
        var studioMinusOne = studio.consumerNtc(-1, null, null, false);
        assertThat(liveMinusOne.value01()).isEqualTo(0);
        assertThat(studioMinusOne.get("value")).isEqualTo(false);

        var liveDi = live.evaluateStatusNtc(null, null, false, null);
        var studioDi = studio.consumerNtc(null, null, null, false);
        assertThat(liveDi.outcome()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(studioDi.get("outcome")).isEqualTo("DATA_INSUFFICIENT");

        var liveStatus = live.evaluateStatusNtc(null, null, false, "NTC");
        var studioStatus = studio.consumerNtc(null, "NTC", null, false);
        assertThat(liveStatus.value01()).isEqualTo(1);
        assertThat(studioStatus.get("value")).isEqualTo(true);
    }

    private static void assertDecision(
            PolicyDslInterpreterV1 interp,
            Map<String, Object> metrics,
            Map<String, Object> facts,
            boolean expectedAllow) {
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(metrics, facts, null);
        String outcome = interp.evaluate(COMPOUND, ctx);
        boolean allow = PolicyDslInterpreterV1.PASS.equals(outcome);
        assertThat(allow).as("EXPECTED=%s ACTUAL=%s metrics=%s facts=%s",
                expectedAllow ? "ALLOW" : "REJECT", outcome, metrics, facts).isEqualTo(expectedAllow);
    }
}
