package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-READINESS-SINGLE-SOURCE-OF-TRUTH-1 — Ready checklist cannot disagree with submit blockers.
 */
class PolicyReadinessSingleSourceOfTruthTest {

    @Test
    void openHundredBoundary_cannotReportExecutionReadyWhileSubmitBlocked() {
        PolicyStudioSession session = sessionWithOpenHundredBoundary();
        Map<String, Object> eval = PolicyExecutionReadiness.evaluate(session);
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.sessionExecutionBlockers(session);

        assertThat(blockers).isNotEmpty();
        assertThat(eval.get("executionReady")).isEqualTo(false);
        assertThat(((Number) eval.get("executionReadyRules")).intValue())
                .isLessThan(((Number) eval.get("includedExecutableRules")).intValue());
        assertThat(eval.get("boundaryAmbiguitiesResolved")).isEqualTo(false);

        // Invariant: if execute blockers exist, "ready for next" checklist inputs must not all be green
        boolean rulesReadyItemWouldBeOk = Boolean.TRUE.equals(eval.get("executionReady"));
        assertThat(rulesReadyItemWouldBeOk).isFalse();
        assertThat(blockers.stream().anyMatch(b ->
                String.valueOf(b.get("reason")).contains("transaction count = 100"))).isTrue();
    }

    @Test
    void afterBoundaryResolved_noHundredBlocker_andExecutionReady() {
        PolicyStudioSession session = sessionWithOpenHundredBoundary();
        CiPolicyRuleCandidate rule = session.getRuleCandidates().get(0);
        Map<String, Object> patched = InwardReturnCompoundSupport.patchBoundary(
                rule.getExpression(), InwardReturnCompoundSupport.OPTION_RATIO);
        rule.setExpression(patched);
        Map<String, Object> meta = new LinkedHashMap<>(rule.getMetadata());
        meta.put("boundaryResolved", true);
        meta.put("boundaryOption", InwardReturnCompoundSupport.OPTION_RATIO);
        meta.put("businessSummary", InwardReturnCompoundSupport.businessSummaryAfterBoundary(
                InwardReturnCompoundSupport.OPTION_RATIO));
        rule.setMetadata(meta);
        for (CiPolicyAmbiguity a : session.getAmbiguities()) {
            a.setResolutionStatus("RESOLVED");
            a.setResolvedOption(InwardReturnCompoundSupport.OPTION_RATIO);
        }

        Map<String, Object> eval = PolicyExecutionReadiness.evaluate(session);
        assertThat(eval.get("boundaryAmbiguitiesResolved")).isEqualTo(true);
        assertThat(PolicyExecutionReadiness.sessionExecutionBlockers(session))
                .noneMatch(b -> String.valueOf(b.get("reason")).contains("transaction count = 100"));
        // Boundary closed ≠ structurally executable: inward-return ratio is NOT_READY
        // (CALCULATION_NOT_DEFINED) in this unit JVM. executionReady follows canonical param truth.
        assertThat(eval.get("requiredParametersResolved")).isEqualTo(false);
        assertThat(eval.get("currentExecutionReadiness")).isEqualTo("BLOCKED");
    }

    @Test
    void staleOpenAmbiguityIgnoredWhenRuleBoundaryAlreadyClosed() {
        PolicyStudioSession session = sessionWithOpenHundredBoundary();
        CiPolicyRuleCandidate rule = session.getRuleCandidates().get(0);
        rule.setExpression(InwardReturnCompoundSupport.patchBoundary(
                rule.getExpression(), InwardReturnCompoundSupport.OPTION_RATIO));
        Map<String, Object> meta = new LinkedHashMap<>(rule.getMetadata());
        meta.put("boundaryResolved", true);
        meta.put("boundaryOption", InwardReturnCompoundSupport.OPTION_RATIO);
        rule.setMetadata(meta);
        // leave ambiguity OPEN — must not block current version
        assertThat(session.getAmbiguities().get(0).getResolutionStatus()).isEqualTo("OPEN");

        List<Map<String, Object>> blockers = PolicyExecutionReadiness.sessionExecutionBlockers(session);
        assertThat(blockers).noneMatch(b ->
                String.valueOf(b.get("reason")).contains("transaction count = 100"));
        assertThat(PolicyExecutionReadiness.evaluate(session).get("boundaryAmbiguitiesResolved"))
                .isEqualTo(true);
    }

    private static PolicyStudioSession sessionWithOpenHundredBoundary() {
        Map<String, Object> expr = PolicyDsl.iff(
                PolicyDsl.gt(PolicyDsl.metric(InwardReturnCompoundSupport.TXN_METRIC), 100),
                PolicyDsl.lte(PolicyDsl.metric(InwardReturnCompoundSupport.RATIO_METRIC), 5),
                PolicyDsl.lte(PolicyDsl.metric(InwardReturnCompoundSupport.COUNT_METRIC), 5));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("disposition", "ACCEPTED");
        meta.put("businessTitle", "Inward cheque / ECS / ENACH returns");
        meta.put("businessSummary",
                "If transactions > 100 in last 3 months: return ratio ≤ 5%; if < 100: return count ≤ 5");
        CiPolicyRuleCandidate rule = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .systemRuleId("BANK_INWARD_RETURN_BRANCHED_100")
                .expression(expr)
                .metadata(meta)
                .build();
        CiPolicyAmbiguity amb = CiPolicyAmbiguity.builder()
                .id(UUID.randomUUID())
                .phrase("exactly 100 transactions")
                .severity("MATERIAL")
                .ambiguityType("BOUNDARY_AMBIGUITY")
                .resolutionStatus("OPEN")
                .build();
        PolicyStudioSession session = new PolicyStudioSession();
        session.getRuleCandidates().add(rule);
        session.getAmbiguities().add(amb);
        return session;
    }
}
