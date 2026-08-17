package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.lifecycle.PolicyRuleLenderState;
import com.los.core.creditintelligence.policystudio.parameters.lifecycle.PolicyRuleParticipation;
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
 * POLICY-RULE-PARTICIPATION-READINESS-INVARIANT-1 — cases A–G.
 * Parameter catalogue truth is not mutated; IGNORED only drops rule requirements.
 */
class PolicyRuleParticipationReadinessInvariantTest {

    private static final String NOT_READY_PARAM = "bureau.thin_file_indicator";
    private static final String READY_PARAM = "bureau.score";

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
    void vocabulary_policyRequirementParticipates_ignoredDoesNot() {
        assertThat(PolicyRuleParticipation.participates(PolicyRuleLenderState.POLICY_REQUIREMENT)).isTrue();
        assertThat(PolicyRuleParticipation.participates(PolicyRuleLenderState.ACCEPTED_READY_TO_TEST)).isTrue();
        assertThat(PolicyRuleParticipation.participates(PolicyRuleLenderState.NEEDS_INPUT)).isTrue();
        assertThat(PolicyRuleParticipation.participates(PolicyRuleLenderState.IGNORED)).isFalse();
        assertThat(PolicyRuleParticipation.participates(PolicyRuleLenderState.NOT_APPLICABLE)).isFalse();
    }

    @Test
    void caseA_notReady_onlyIgnoredRule_isNotPolicyBlocker() {
        PolicyStudioSession session = sessionOf(rule(NOT_READY_PARAM, "IGNORED", false, "CM_A"));
        assertThat(CanonicalParameterStateService.state(NOT_READY_PARAM).get("businessReadiness"))
                .isEqualTo("NOT_READY");
        assertThat(PolicyRuleParticipation.classify(session.getRuleCandidates().get(0)))
                .isEqualTo(PolicyRuleParticipation.Kind.NON_PARTICIPATING);
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session)).isEmpty();
        assertThat(PolicyExecutionReadiness.countNeedsBusinessInput(session)).isZero();
    }

    @Test
    void caseB_notReady_policyRequirementRule_isPolicyBlocker() {
        PolicyStudioSession session = sessionOf(
                rule(NOT_READY_PARAM, "KEEP_AS_POLICY_REQUIREMENT", false, "CM_B"));
        assertThat(PolicyRuleParticipation.participatesInPolicyReadiness(session.getRuleCandidates().get(0)))
                .isTrue();
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session))
                .anyMatch(b -> NOT_READY_PARAM.equals(String.valueOf(b.get("canonicalParameterId"))));
    }

    @Test
    void caseC_sharedParameter_ignoredPlusRequirement_oneBlocker() {
        PolicyStudioSession session = sessionOf(
                rule(NOT_READY_PARAM, "IGNORED", false, "CM_C_IGN"),
                rule(NOT_READY_PARAM, "KEEP_AS_POLICY_REQUIREMENT", false, "CM_C_REQ"));
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.currentParameterBlockers(session);
        assertThat(blockers).hasSize(1);
        assertThat(blockers.get(0).get("canonicalParameterId")).isEqualTo(NOT_READY_PARAM);
    }

    @Test
    void caseD_readyParameter_policyRequirement_noBlocker() {
        String readyId = java.util.stream.Stream.of(
                        "kyc.pan.verified",
                        "application.loan_amount",
                        "bureau.score",
                        READY_PARAM)
                .filter(id -> "READY".equals(String.valueOf(
                        CanonicalParameterStateService.state(id).get("businessReadiness"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No READY canonical parameter in this JVM"));
        PolicyStudioSession session = sessionOf(
                rule(readyId, "KEEP_AS_POLICY_REQUIREMENT", false, "CM_D"));
        assertThat(PolicyRuleParticipation.participatesInPolicyReadiness(session.getRuleCandidates().get(0)))
                .isTrue();
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session))
                .noneMatch(b -> readyId.equals(String.valueOf(b.get("canonicalParameterId"))));
    }

    @Test
    void caseE_notReady_deletedRuleOnly_noBlocker() {
        PolicyStudioSession session = sessionOf(rule(NOT_READY_PARAM, "DELETED", true, "CM_E"));
        assertThat(PolicyRuleParticipation.classify(session.getRuleCandidates().get(0)))
                .isEqualTo(PolicyRuleParticipation.Kind.DELETED_DEAD);
        assertThat(CanonicalParameterStateService.state(NOT_READY_PARAM).get("businessReadiness"))
                .isEqualTo("NOT_READY");
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session)).isEmpty();
    }

    @Test
    void caseF_compoundChild_doesNotDuplicateBlocker() {
        CiPolicyRuleCandidate parent = rule(NOT_READY_PARAM, "ACCEPTED", false, "CM_COMPOUND_PARENT");
        CiPolicyRuleCandidate child = rule(NOT_READY_PARAM, "ACCEPTED", false, "CM_COMPOUND_CHILD_1");
        PolicyStudioSession session = sessionOf(parent, child);
        assertThat(PolicyRuleParticipation.classify(child))
                .isEqualTo(PolicyRuleParticipation.Kind.CONDITIONAL_CHILD);
        assertThat(PolicyRuleParticipation.participatesInPolicyReadiness(parent)).isTrue();
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session)).hasSize(1);
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session).get(0).get("canonicalParameterId"))
                .isEqualTo(NOT_READY_PARAM);
    }

    @Test
    void caseG_sameParameterThreeParticipatingRules_oneUniqueBlocker() {
        PolicyStudioSession session = sessionOf(
                rule(NOT_READY_PARAM, "ACCEPTED", false, "CM_G1"),
                rule(NOT_READY_PARAM, "ACCEPTED", false, "CM_G2"),
                rule(NOT_READY_PARAM, "ACCEPTED", false, "CM_G3"));
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session)).hasSize(1);
        long participating = session.getRuleCandidates().stream()
                .filter(PolicyRuleParticipation::participatesInPolicyReadiness)
                .count();
        assertThat(participating).isEqualTo(3);
    }

    @Test
    void ignoredOnlyShared_twoIgnoredRules_zeroBlockers() {
        PolicyStudioSession session = sessionOf(
                rule(NOT_READY_PARAM, "IGNORED", false, "CM_IGN1"),
                rule(NOT_READY_PARAM, "IGNORED", false, "CM_IGN2"));
        assertThat(PolicyExecutionReadiness.currentParameterBlockers(session)).isEmpty();
        assertThat(CanonicalParameterStateService.state(NOT_READY_PARAM).get("businessReadiness"))
                .isEqualTo("NOT_READY");
    }

    private static PolicyStudioSession sessionOf(CiPolicyRuleCandidate... rules) {
        PolicyStudioSession session = new PolicyStudioSession();
        session.getRuleCandidates().addAll(List.of(rules));
        return session;
    }

    private static CiPolicyRuleCandidate rule(
            String parameterId, String disposition, boolean deleted, String systemId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("disposition", disposition);
        meta.put("deleted", deleted);
        meta.put("excludedFromActivation",
                "IGNORED".equals(disposition)
                        || "DELETED".equals(disposition)
                        || "KEEP_AS_POLICY_REQUIREMENT".equals(disposition));
        meta.put("parameterId", parameterId);
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .systemRuleId(systemId)
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", parameterId),
                        "right", Map.of("const", 0)))
                .metadata(meta)
                .build();
    }
}
