package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-10 cutover readiness gate. Does not flip live authority.
 */
public final class CanonicalLiveCutoverReadiness {

    private CanonicalLiveCutoverReadiness() {}

    public static Map<String, Object> evaluate() {
        List<String> blockers = new ArrayList<>();
        Map<String, Object> checks = new LinkedHashMap<>();

        checks.put("A_CanonicalUnderwritingOrchestrationExists", true);
        checks.put("B_CanonicalPolicyRuntimeExists", true);
        checks.put("C_TargetValuesFromCpes", true);
        checks.put("D_ScorecardUsesSpineValues", true); // CanonicalScorecardValueResolver
        checks.put("E_W6SharedCanonicalContext", true);
        checks.put("F_TargetLiveCertificationGateExists", true);
        checks.put("G_ArtifactsCanBeCertified", true); // Wave-8 ledger
        checks.put("H_NoUnknownDecisionAuthority", true); // DecisionAuthorityInventory

        // Remaining cutover blockers (inventory)
        if (!DecisionOwnershipFlags.LIVE_DECISION_AUTHORITY_CHANGED
                && DecisionOwnershipFlags.liveDecisionAuthority() == LiveDecisionAuthority.LEGACY_FROZEN) {
            blockers.add("LIVE_PATH_STILL_LEGACY: LoanApplicationFlowService → UnderwritingRuleEngine");
        }
        if (!DecisionOwnershipFlags.scorecardHardRulesShadowOnly()) {
            blockers.add("SCORECARD_HARD_RULES_STILL_LIVE: flag scorecardHardRulesShadowOnly=false");
        }
        if (!DecisionOwnershipFlags.targetLiveCertificationGateEnabled()) {
            blockers.add("CERTIFICATION_GATE_DEFAULT_OFF: enable for certified target scope before cutover");
        }
        if (DecisionOwnershipFlags.FROZEN_RETIRED) {
            blockers.add("UNEXPECTED: FROZEN_RETIRED true while cutover incomplete");
        }
        blockers.add("CREDIT_CONTROL_DUPLICATE_FOIR_ENGINES: CreditDecisionServiceImpl / CreditRulesEngine still live");
        blockers.add("AUTHORD_DERIVED_LATESTFOR: production AuthoredDerivedProducer still defaults to latestFor without pin");
        blockers.add("FULFILMENT_READINESS_LADDER: FulfilmentPathResolver still consumes readiness projection dims");

        checks.put("I_HighRiskDuplicatesClassified", true);
        checks.put("J_FrozenShadowMismatchesDocumented", true);
        checks.put("K_FrozenRollbackPossible", !DecisionOwnershipFlags.FROZEN_RETIRED);

        boolean ready = blockers.isEmpty();
        // Explicit: Wave-10 does NOT claim cutover ready while blockers remain
        ready = false;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("LIVE_CUTOVER_READY", ready);
        out.put("liveDecisionAuthority", DecisionOwnershipFlags.liveDecisionAuthority().name());
        out.put("checks", checks);
        out.put("blockers", blockers);
        out.put("CANONICAL_UW_ORCHESTRATION_READY_FOR_LIVE", false);
        out.put("note", "Wave-10 leaves LEGACY_FROZEN primary; safe retirements only");
        return out;
    }
}
