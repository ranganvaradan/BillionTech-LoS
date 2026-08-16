package com.los.core.creditintelligence.policystudio.runtime;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Target-live <em>canonical</em> policy evaluation entry (Wave-6).
 * Uses the same {@link CanonicalPolicyRuntime} as Policy Test / Graph / shadow.
 * Does <strong>not</strong> replace live UnderwritingRuleEngine / ScorecardPolicyEngine authority.
 */
public final class TargetLiveCanonicalPolicyEvaluation {

    public static final String PATH = "TargetLiveCanonicalPolicyEvaluation";

    private final CanonicalPolicyRuntime runtime;

    public TargetLiveCanonicalPolicyEvaluation(CanonicalParameterExecutionService cpes) {
        this.runtime = new CanonicalPolicyRuntime(Objects.requireNonNull(cpes, "cpes"));
    }

    public TargetLiveCanonicalPolicyEvaluation(CanonicalPolicyRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public CanonicalPolicyRuntime runtime() {
        return runtime;
    }

    public CanonicalPolicyResult evaluate(
            String policyId,
            String policyVersion,
            List<CanonicalPolicyRuntime.RuleSpec> rules,
            EvaluationContext sharedContext,
            LocalDate evaluationAsOf) {
        LocalDate asOf = evaluationAsOf != null
                ? evaluationAsOf
                : (sharedContext == null ? null : sharedContext.evaluationAsOf());
        if (asOf == null) {
            throw new IllegalArgumentException(
                    "Target-live canonical evaluation requires explicit evaluationAsOf");
        }
        EvaluationContext ctx = SharedCanonicalEvaluationSupport.normalize(
                sharedContext,
                sharedContext != null && sharedContext.mode() != null
                        ? sharedContext.mode()
                        : EvaluationMode.UNDERWRITING,
                asOf);
        CanonicalPolicyResult result = runtime.evaluate(new CanonicalPolicyRuntime.PolicyRequest(
                policyId, policyVersion, rules, ctx, asOf));
        // Observational / target-path only — never live cutover
        result.meta().put("evaluationPath", PATH);
        result.meta().put("liveDecisionAuthorityChanged", false);
        return result;
    }
}
