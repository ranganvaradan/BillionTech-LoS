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
