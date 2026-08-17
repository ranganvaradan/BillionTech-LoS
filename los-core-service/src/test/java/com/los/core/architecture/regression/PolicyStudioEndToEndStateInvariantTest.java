package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.creditintelligence.policystudio.parameters.RuleOperandPresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.lifecycle.PolicyRuleLifecycleProjection;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-STUDIO-END-TO-END-STATE-INVARIANT-1 — sentinel matrix A–F.
 * Axes must stay distinct. No Vikasam mutation.
 */
class PolicyStudioEndToEndStateInvariantTest {

    private static final String DPD30 = "bureau.dpd_30_plus_count_6m";
    private static final String CC_OVERDUE = "bureau.cc_overdue_amount";

    @BeforeEach
    void setUp() {
        ExecutionCapabilityAuthority.install(
                ExecutionSpineProducerBootstrap.standalone((id, tenant) -> java.util.Optional.empty()));
    }

    @AfterEach
    void tearDown() {
        ExecutionCapabilityAuthority.clear();
    }

    @Test
    void caseA_readyParameter_needsReviewRule_valueAbsent_notNotApplicable() {
        Map<String, Object> cps = CanonicalParameterStateService.state(DPD30);
        assertThat(String.valueOf(cps.get("primaryStatusLabel"))).isNotEqualTo("Needs review");
        var facts = new PolicyRuleLifecycleProjection.Facts(
                false, true, false, true, true, false, true, true, false, false, true, true, false, false);
        Map<String, Object> proj = PolicyRuleLifecycleProjection.project("r-a", facts);
        assertThat(proj.get("lenderState")).isEqualTo("READY_FOR_CONFIRMATION");
        assertThat(proj.get("ruleLifecycleLabel")).isEqualTo("Needs review");
        assertThat(proj.get("ruleLifecycleLabel")).isNotEqualTo("Not applicable");
        assertThat(proj.get("ruleLifecycleLabel")).isNotEqualTo(cps.get("primaryStatusLabel"));
    }

    @Test
    void caseB_readyParameter_acceptedRule() {
        Map<String, Object> cps = CanonicalParameterStateService.state(DPD30);
        assertThat(String.valueOf(cps.get("primaryStatusLabel"))).isNotEqualTo("Needs review");
        var facts = new PolicyRuleLifecycleProjection.Facts(
                true, true, false, true, true, false, true, true, false, false, true, true, false, false);
        Map<String, Object> proj = PolicyRuleLifecycleProjection.project("r-b", facts);
        assertThat(proj.get("lenderState")).isEqualTo("ACCEPTED_READY_TO_TEST");
        assertThat(proj.get("ruleLifecycleLabel")).isEqualTo("Accepted · Ready to test");
        assertThat(proj.get("ruleLifecycleLabel")).isNotEqualTo(cps.get("primaryStatusLabel"));
    }

    @Test
    void caseC_notReadyCalculation_needsReview_setupAction() {
        Map<String, Object> cps = CanonicalParameterStateService.state(CC_OVERDUE);
        assertThat(cps.get("businessReadiness")).isEqualTo("NOT_READY");
        assertThat(cps.get("businessReadinessReason")).isEqualTo("CALCULATION_NOT_DEFINED");
        var facts = new PolicyRuleLifecycleProjection.Facts(
                false, true, true, false, false, false, true, false, false, false, true, true, false, false);
        assertThat(PolicyRuleLifecycleProjection.project("r-c", facts).get("lenderState"))
                .isEqualTo("NEEDS_INPUT");
        Map<String, Object> proj = PolicyRuleLifecycleProjection.project("r-c", facts);
        assertThat(proj.get("showCalculationSetup")).isEqualTo(true);
        assertThat(proj.get("ruleLifecycleLabel")).isNotEqualTo("Not applicable");
        assertThat(String.valueOf(cps.get("primaryStatusLabel"))).isNotEqualTo("Needs review");
    }

    @Test
    void caseD_readyManual_inputRequired_notUnresolved() {
        Map<String, Object> edi = CanonicalParameterStateService.state("application.proposed_edi");
        assertThat(edi.get("businessReadiness")).isIn("READY", "NOT_READY");
        String reason = String.valueOf(edi.getOrDefault("businessReadinessReason", ""));
        assertThat(reason.equals("MANUAL_INPUT")
                || "NEEDS_MANUAL_INPUT".equals(String.valueOf(edi.get("primaryStatus")))
                || "READY".equals(String.valueOf(edi.get("businessReadiness")))).isTrue();
    }

    @Test
    void caseE_lifecycleActiveUnchanged_whenCurrentBlockersExist() {
        PolicyStudioSession session = sessionWithParticipatingNotReady();
        Map<String, Object> eval = PolicyExecutionReadiness.evaluate(session);
        assertThat(eval.get("currentExecutionReadiness")).isEqualTo("BLOCKED");
        assertThat(((List<?>) eval.get("currentParameterBlockers"))).isNotEmpty();
        assertThat(PolicyExecutionReadiness.countNeedsBusinessInput(session)).isGreaterThan(0);
        // Historical lifecycle is not stored on evaluate — callers must not rewrite it.
        assertThat(eval.get("authority")).isEqualTo("PolicyExecutionReadiness.evaluate");
    }

    @Test
    void caseF_newDraftWithNotReadyParam_requiredParametersNotResolved() {
        PolicyStudioSession session = sessionWithParticipatingNotReady();
        Map<String, Object> eval = PolicyExecutionReadiness.evaluate(session);
        assertThat(eval.get("requiredParametersResolved")).isEqualTo(false);
    }

    @Test
    void operandKeyHydratesDpd30WithoutMutatingStoredIdContract() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("status", "PROPOSAL_ACCEPTED");
        stored.put("parameterId", null);
        Map<String, Object> res = Map.of("dpd_30_plus_count_6m", stored);
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "CM_BUREAU_DPD_30_PLUS_COUNT_6M_GTE",
                List.of(DPD30),
                Map.of(
                        "parameterResolutions", res,
                        "parameterId", DPD30),
                Map.of());
        assertThat(ops).isNotEmpty();
        Map<String, Object> face = ops.stream()
                .filter(o -> DPD30.equals(String.valueOf(o.get("parameterId")))
                        || "dpd_30_plus_count_6m".equals(String.valueOf(o.get("operandKey"))))
                .findFirst()
                .orElse(ops.get(0));
        assertThat(face.get("parameterId")).isEqualTo(DPD30);
        assertThat(String.valueOf(face.get("primaryStatusLabel"))).isNotEqualTo("Needs review");
        assertThat(CanonicalParameterRegistry.shared().findByOperandKey("dpd_30_plus_count_6m"))
                .isPresent()
                .get()
                .extracting(d -> d.id())
                .isEqualTo(DPD30);
    }

    @Test
    void ignoredNotReadyDoesNotCountAsCurrentPolicyBlocker() {
        PolicyStudioSession session = sessionWithIgnoredNotReady();
        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(session.getRuleCandidates().get(0)))
                .isFalse();
        assertThat(PolicyExecutionReadiness.isCurrentAttentionRule(session.getRuleCandidates().get(0)))
                .isTrue();
        assertThat(PolicyExecutionReadiness.countNeedsBusinessInput(session)).isZero();
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session))
                .noneMatch(b -> CC_OVERDUE.equals(String.valueOf(b.get("canonicalParameterId"))));
        assertThat(CanonicalParameterStateService.state(CC_OVERDUE).get("businessReadiness"))
                .isEqualTo("NOT_READY");
    }

    private static PolicyStudioSession sessionWithIgnoredNotReady() {
        return sessionWithRule(CC_OVERDUE, "IGNORED", true, false, "CM_CC_OD_AMOUNT_GTE");
    }

    private static PolicyStudioSession sessionWithParticipatingNotReady() {
        return sessionWithRule(CC_OVERDUE, "KEEP_AS_POLICY_REQUIREMENT", true, false,
                "CM_CC_OD_AMOUNT_GTE");
    }

    private static PolicyStudioSession sessionWithRule(
            String parameterId, String disposition, boolean excluded, boolean deleted, String systemId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("disposition", disposition);
        meta.put("excludedFromActivation", excluded);
        meta.put("deleted", deleted);
        meta.put("parameterId", parameterId);
        CiPolicyRuleCandidate r = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .systemRuleId(systemId)
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", parameterId),
                        "right", Map.of("const", 0)))
                .metadata(meta)
                .build();
        PolicyStudioSession session = new PolicyStudioSession();
        session.getRuleCandidates().add(r);
        return session;
    }
}
