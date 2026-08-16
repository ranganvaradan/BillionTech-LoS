package com.los.core.creditintelligence.policystudio.runtime.ownership;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.SharedCanonicalEvaluationSupport;
import com.los.core.creditintelligence.policystudio.runtime.TargetLiveCanonicalPolicyEvaluation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Target underwriting orchestration (Wave-7).
 *
 * <pre>
 * EvaluationContext → CPES (via CPR) → CanonicalPolicyRuntime
 *   → Scorecard band outcome (input; no canonical recalculation)
 *   → limit/pricing stubs
 *   → audited manual override
 *   → FinalUnderwritingDecision
 * </pre>
 *
 * Does not replace live {@code LoanApplicationFlowService}. Does not recalculate canonical values.
 */
public final class CanonicalUnderwritingOrchestration {

    public static final String PATH = "CanonicalUnderwritingOrchestration";

    private final TargetLiveCanonicalPolicyEvaluation policyEval;

    public CanonicalUnderwritingOrchestration(CanonicalParameterExecutionService cpes) {
        this.policyEval = new TargetLiveCanonicalPolicyEvaluation(Objects.requireNonNull(cpes));
    }

    public CanonicalUnderwritingOrchestration(CanonicalPolicyRuntime runtime) {
        this.policyEval = new TargetLiveCanonicalPolicyEvaluation(runtime);
    }

    public record ScorecardBandInput(
            FinalUnderwritingDecision.FinalOutcome bandOutcome,
            Map<String, Object> detail,
            boolean hardRulesDeferredToPolicy
    ) {}

    public FinalUnderwritingDecision assemble(
            String applicationId,
            LocalDate evaluationAsOf,
            EvaluationContext sharedContext,
            String policyId,
            String policyVersion,
            List<CanonicalPolicyRuntime.RuleSpec> policyRules,
            ScorecardBandInput scorecard,
            Map<String, Object> limitResult,
            Map<String, Object> pricingResult,
            ManualOverrideRecord override) {

        LocalDate asOf = evaluationAsOf != null
                ? evaluationAsOf
                : (sharedContext == null ? null : sharedContext.evaluationAsOf());
        if (asOf == null) {
            throw new IllegalArgumentException("Canonical UW orchestration requires explicit evaluationAsOf");
        }

        EvaluationContext ctx = SharedCanonicalEvaluationSupport.normalize(
                sharedContext,
                sharedContext != null && sharedContext.mode() != null
                        ? sharedContext.mode()
                        : com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode.UNDERWRITING,
                asOf);

        CanonicalPolicyResult policy = policyEval.evaluate(
                policyId, policyVersion,
                policyRules == null ? List.of() : policyRules,
                ctx, asOf);

        FinalUnderwritingDecision.FinalOutcome band = scorecard == null
                ? FinalUnderwritingDecision.FinalOutcome.REFER
                : scorecard.bandOutcome();

        var prec = PolicyScorecardPrecedence.combine(policy, band, override);

        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("orchestration", PATH);
        prov.put("recalculatesCanonicalValues", false);
        prov.put("usesCpesViaPolicyRuntime", true);
        prov.put("liveDecisionAuthorityChanged", false);
        prov.put("frozenRetired", false);
        prov.put("precedence", prec.ruleApplied());
        if (scorecard != null) {
            prov.put("hardRulesDeferredToPolicy", scorecard.hardRulesDeferredToPolicy());
        }
        prov.put("workflowIsNotDecision", true);

        List<String> reasons = new ArrayList<>(prec.reasonCodes());
        if (policy.failedRuleIds() != null) {
            for (String id : policy.failedRuleIds()) reasons.add("POLICY_FAIL:" + id);
        }
        if (policy.insufficientRuleIds() != null) {
            for (String id : policy.insufficientRuleIds()) reasons.add("POLICY_DI:" + id);
        }

        return new FinalUnderwritingDecision(
                applicationId,
                asOf,
                policy.toMap(),
                scorecard == null ? Map.of() : scorecard.detail(),
                limitResult == null ? Map.of() : limitResult,
                pricingResult == null ? Map.of() : pricingResult,
                override,
                prec.outcome(),
                reasons,
                prov);
    }

    /**
     * Target-live path with optional Wave-8 certification gate.
     * Certification failure → {@link FinalUnderwritingDecision.FinalOutcome#LIVE_BLOCKED}
     * (operational), never credit REJECT.
     * Legacy LoanApplicationFlowService is unaffected.
     */
    public FinalUnderwritingDecision assembleTargetLive(
            String applicationId,
            LocalDate evaluationAsOf,
            EvaluationContext sharedContext,
            String policyId,
            String policyVersion,
            List<CanonicalPolicyRuntime.RuleSpec> policyRules,
            ScorecardBandInput scorecard,
            Map<String, Object> limitResult,
            Map<String, Object> pricingResult,
            ManualOverrideRecord override,
            List<String> automaticOperandCanonicalIds,
            String scorecardId,
            String scorecardVersion,
            com.los.core.creditintelligence.policystudio.certification.CertificationScopeType scopeType,
            String scopeId) {

        if (DecisionOwnershipFlags.targetLiveCertificationGateEnabled()) {
            var cert = com.los.core.creditintelligence.policystudio.certification
                    .ProductionCertificationAuthority.get();
            if (cert == null) {
                cert = new com.los.core.creditintelligence.policystudio.certification
                        .ProductionCertificationService();
            }
            Map<String, Object> gate = cert.evaluatePolicyLiveGate(
                    policyId, policyVersion,
                    scopeType == null
                            ? com.los.core.creditintelligence.policystudio.certification.CertificationScopeType.PLATFORM
                            : scopeType,
                    scopeId,
                    automaticOperandCanonicalIds,
                    scorecardId,
                    scorecardVersion);
            if (!Boolean.TRUE.equals(gate.get("livePermitted"))) {
                Map<String, Object> prov = new LinkedHashMap<>();
                prov.put("orchestration", PATH);
                prov.put("certificationGate", gate);
                prov.put("operationalBlock", "NOT_CERTIFIED");
                prov.put("creditReject", false);
                prov.put("liveDecisionAuthorityChanged", false);
                List<String> reasons = new ArrayList<>();
                reasons.add("NOT_CERTIFIED");
                reasons.add("LIVE_BLOCKED");
                Object blockers = gate.get("blockers");
                if (blockers instanceof List<?> list) {
                    for (Object b : list) reasons.add(String.valueOf(b));
                }
                return new FinalUnderwritingDecision(
                        applicationId,
                        evaluationAsOf,
                        Map.of("overall", "NOT_EVALUATED_DUE_TO_CERTIFICATION"),
                        scorecard == null ? Map.of() : scorecard.detail(),
                        limitResult == null ? Map.of() : limitResult,
                        pricingResult == null ? Map.of() : pricingResult,
                        override,
                        FinalUnderwritingDecision.FinalOutcome.LIVE_BLOCKED,
                        reasons,
                        prov);
            }
        }

        FinalUnderwritingDecision credit = assemble(
                applicationId, evaluationAsOf, sharedContext, policyId, policyVersion,
                policyRules, scorecard, limitResult, pricingResult, override);
        Map<String, Object> prov = new LinkedHashMap<>(credit.provenance());
        prov.put("targetLivePath", true);
        prov.put("certificationGateEnforced", DecisionOwnershipFlags.targetLiveCertificationGateEnabled());
        return new FinalUnderwritingDecision(
                credit.applicationId(), credit.evaluationAsOf(), credit.policyResult(),
                credit.scorecardResult(), credit.limitResult(), credit.pricingResult(),
                credit.manualOverride(), credit.finalOutcome(), credit.reasonCodes(), prov);
    }

    /**
     * Wave-10 pinned target-live path. Requires explicit artifact versions — no latestFor.
     * Does not flip live LoanApplicationFlowService authority.
     */
    public FinalUnderwritingDecision assembleTargetLivePinned(
            String applicationId,
            EvaluationContext sharedContext,
            PinnedArtifactSelection pins,
            List<CanonicalPolicyRuntime.RuleSpec> policyRules,
            ScorecardBandInput scorecard,
            Map<String, Object> limitResult,
            Map<String, Object> pricingResult,
            ManualOverrideRecord override,
            List<String> automaticOperandCanonicalIds,
            com.los.core.creditintelligence.policystudio.certification.CertificationScopeType scopeType,
            String scopeId) {

        Objects.requireNonNull(pins, "pins");
        pins.requireForTargetLive();

        EvaluationContext ctx = sharedContext;
        if (ctx != null) {
            Map<String, Object> entities = new LinkedHashMap<>(ctx.entities());
            entities.putAll(pins.entityPins());
            ctx = EvaluationContext.builder()
                    .mode(ctx.mode())
                    .tenantId(ctx.tenantId())
                    .evaluationAsOf(pins.evaluationAsOf())
                    .facts(ctx.facts())
                    .inputs(ctx.inputs())
                    .entities(entities)
                    .build();
        } else {
            ctx = EvaluationContext.builder()
                    .mode(com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode.UNDERWRITING)
                    .evaluationAsOf(pins.evaluationAsOf())
                    .entities(pins.entityPins())
                    .build();
        }

        FinalUnderwritingDecision d = assembleTargetLive(
                applicationId,
                pins.evaluationAsOf(),
                ctx,
                pins.policyId(),
                pins.policyVersion(),
                policyRules,
                scorecard,
                limitResult,
                pricingResult,
                override,
                automaticOperandCanonicalIds,
                pins.scorecardId(),
                pins.scorecardVersion(),
                scopeType,
                scopeId);

        Map<String, Object> prov = new LinkedHashMap<>(d.provenance());
        prov.put("pinnedArtifacts", pins.toMap());
        prov.put("latestForForbidden", true);
        prov.put("liveDecisionAuthority", DecisionOwnershipFlags.liveDecisionAuthority().name());
        prov.put("creditControlDuplicatePolicyNotApplied", true);
        prov.put("scorecardHardRulesNotAppliedOnCanonicalPath", true);
        return new FinalUnderwritingDecision(
                d.applicationId(), d.evaluationAsOf(), d.policyResult(),
                d.scorecardResult(), d.limitResult(), d.pricingResult(),
                d.manualOverride(), d.finalOutcome(), d.reasonCodes(), prov);
    }

    /** Workflow readiness never becomes APPROVE. */
    public static FinalUnderwritingDecision.FinalOutcome workflowStateToDecision(String workflowState) {
        if (workflowState == null) return FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
        return switch (workflowState.trim().toUpperCase()) {
            case "COMPLETE", "READY", "ACQUIRED", "READY_FOR_EVALUATION" ->
                    FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT; // readiness only
            case "BLOCKED", "INCOMPLETE" -> FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
            default -> FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
        };
    }
}
