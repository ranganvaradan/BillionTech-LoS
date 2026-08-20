package com.los.core.creditintelligence.policystudio.runtime.ownership;

import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalRuleResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic Policy vs Scorecard precedence (Wave-7). Not lender-specific.
 *
 * <pre>
 * Policy FAIL → cannot APPROVE from score
 * Policy DI → cannot silently score into APPROVE
 * Policy PASS → scorecard may APPROVE/REFER/REJECT by band
 * Policy PASS + no scorecard configured → hard-rule-only policy stands on its own (APPROVE)
 * Manual override → explicit layer on top
 * </pre>
 */
public final class PolicyScorecardPrecedence {

    private PolicyScorecardPrecedence() {}

    public record PrecedenceResult(
            FinalUnderwritingDecision.FinalOutcome outcome,
            List<String> reasonCodes,
            String ruleApplied
    ) {}

    public static PrecedenceResult combine(
            CanonicalPolicyResult policy,
            FinalUnderwritingDecision.FinalOutcome scorecardBandOutcome,
            ManualOverrideRecord override) {
        return combine(policy, scorecardBandOutcome, override, false);
    }

    /**
     * @param scorecardExplicitlyAbsent true when the policy is deliberately hard-rule-only
     *      (no scorecard linked) — as opposed to a scorecard being configured but unable to
     *      produce a band. Only changes behavior when the Policy itself PASSes: a hard-rule-only
     *      policy that passes stands on its own (APPROVE) instead of defaulting to REFER.
     */
    public static PrecedenceResult combine(
            CanonicalPolicyResult policy,
            FinalUnderwritingDecision.FinalOutcome scorecardBandOutcome,
            ManualOverrideRecord override,
            boolean scorecardExplicitlyAbsent) {

        List<String> reasons = new ArrayList<>();
        if (policy == null) {
            reasons.add("POLICY_RESULT_MISSING");
            return new PrecedenceResult(FinalUnderwritingDecision.FinalOutcome.ERROR, reasons, "MISSING_POLICY");
        }

        FinalUnderwritingDecision.FinalOutcome fromPolicy = mapPolicy(policy, reasons);
        FinalUnderwritingDecision.FinalOutcome combined;

        if (fromPolicy == FinalUnderwritingDecision.FinalOutcome.REJECT) {
            combined = FinalUnderwritingDecision.FinalOutcome.REJECT;
            reasons.add("POLICY_FAIL_BLOCKS_APPROVE");
        } else if (fromPolicy == FinalUnderwritingDecision.FinalOutcome.ERROR) {
            combined = FinalUnderwritingDecision.FinalOutcome.ERROR;
            reasons.add("POLICY_ERROR");
        } else if (fromPolicy == FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT
                || fromPolicy == FinalUnderwritingDecision.FinalOutcome.REFER) {
            // Do not silently score into APPROVE
            if (scorecardBandOutcome == FinalUnderwritingDecision.FinalOutcome.APPROVE) {
                combined = fromPolicy == FinalUnderwritingDecision.FinalOutcome.REFER
                        ? FinalUnderwritingDecision.FinalOutcome.REFER
                        : FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
                reasons.add("POLICY_DI_OR_REFER_BLOCKS_SCORE_APPROVE");
            } else if (scorecardBandOutcome == FinalUnderwritingDecision.FinalOutcome.REJECT) {
                combined = FinalUnderwritingDecision.FinalOutcome.REJECT;
                reasons.add("SCORECARD_REJECT_WITH_POLICY_INSUFFICIENT");
            } else {
                combined = fromPolicy;
                reasons.add("POLICY_INSUFFICIENT_OR_REFER");
            }
        } else if (scorecardExplicitlyAbsent) {
            // Policy PASS, no scorecard configured — hard-rule-only policy stands on its own.
            combined = FinalUnderwritingDecision.FinalOutcome.APPROVE;
            reasons.add("POLICY_PASS_NO_SCORECARD_HARD_RULES_ONLY");
        } else {
            // Policy PASS
            combined = scorecardBandOutcome == null
                    ? FinalUnderwritingDecision.FinalOutcome.REFER
                    : scorecardBandOutcome;
            reasons.add("POLICY_PASS_SCORECARD_BAND");
        }

        String rule = "POLICY_THEN_SCORECARD";
        if (override != null && override.newOutcome() != null) {
            reasons.add("MANUAL_OVERRIDE_APPLIED");
            rule = "MANUAL_OVERRIDE";
            combined = override.newOutcome();
        }
        return new PrecedenceResult(combined, reasons, rule);
    }

    private static FinalUnderwritingDecision.FinalOutcome mapPolicy(
            CanonicalPolicyResult policy, List<String> reasons) {
        return switch (policy.overall()) {
            case FAIL -> FinalUnderwritingDecision.FinalOutcome.REJECT;
            case PASS -> FinalUnderwritingDecision.FinalOutcome.APPROVE; // eligibility pass only
            case ERROR -> FinalUnderwritingDecision.FinalOutcome.ERROR;
            case DATA_INSUFFICIENT -> {
                // If any rule explicitly REFER-shaped via reason — treat DI as DI
                for (CanonicalRuleResult r : policy.ruleResults()) {
                    if (r.reason() != null && r.reason().contains("onMissing=REFER")) {
                        reasons.add("POLICY_REFER");
                        yield FinalUnderwritingDecision.FinalOutcome.REFER;
                    }
                }
                yield FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
            }
        };
    }

    public static Map<String, Object> rulesDocument() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyFailBlocksApprove", true);
        m.put("policyDiBlocksSilentScoreApprove", true);
        m.put("policyPassDefersToScorecardBand", true);
        m.put("policyPassNoScorecardApproves", true);
        m.put("manualOverrideExplicit", true);
        m.put("workflowNeverApproves", true);
        return m;
    }
}
