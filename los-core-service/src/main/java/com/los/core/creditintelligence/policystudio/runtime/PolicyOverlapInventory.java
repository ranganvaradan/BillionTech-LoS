package com.los.core.creditintelligence.policystudio.runtime;

import com.los.core.creditintelligence.policystudio.runtime.ownership.CreditControlDecisionBoundary;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DecisionAuthorityInventory;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DecisionOwnershipFlags;
import com.los.core.creditintelligence.policystudio.runtime.ownership.DuplicateBusinessConditionInventory;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FoirAuthorityBoundary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-7 ownership inventory facade (extends Wave-5 overlap docs).
 */
public final class PolicyOverlapInventory {

    private PolicyOverlapInventory() {}

    public static List<Map<String, Object>> scorecardOverlaps() {
        return List.of(
                row("ScorecardPolicyEngine.hardRules", "POLICY_ELIGIBILITY",
                        "Hard rules = eligibility; shadow-only flag available",
                        "WAVE_7"),
                row("ScorecardPolicyEngine.bands", "SCORECARD_SCORING",
                        "Weighted band scoring — not policy runtime",
                        "PRESERVE"),
                row("FormulaEvaluator", "SCORECARD_SCORING",
                        "COMPUTED scorecard formulas",
                        "PRESERVE")
        );
    }

    public static List<Map<String, Object>> creditControlOverlaps() {
        return List.of(
                row("CreditControlService.buildEffectiveContext", "CREDIT_CONTROL_PREPROCESSING",
                        "Fact preparation only",
                        "WAVE_7"),
                row("CreditControlService.FOIR_prep", "FACT_PREPARATION",
                        "Policy owns FOIR eligibility threshold",
                        "WAVE_7"),
                row("CreditControlService.DEMO_GAP_defaults", "DEMO_FALLBACK",
                        "Not decision truth when demoDefaultsNotDecisionTruth",
                        "WAVE_7"),
                row("CreditControlService.SCF_MIN_ANNUAL_GST_TURNOVER", "LIMIT_CONTROL",
                        "Hardcoded SCF minimum turnover",
                        "WAVE_7"),
                row("ApplicationScorecardParameterResolver.age", "FACT_PREPARATION",
                        "Explicit asOf (Wave-6); age knockout → Policy",
                        "WAVE_7")
        );
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scorecardPolicyOverlaps", scorecardOverlaps());
        m.put("creditControlPolicyOverlaps", creditControlOverlaps());
        m.put("wave5MigratesOwnership", false);
        m.put("wave7OwnershipModel", true);
        m.put("decisionAuthority", DecisionAuthorityInventory.snapshot());
        m.put("duplicateConditions", DuplicateBusinessConditionInventory.snapshot());
        m.put("foirAuthority", FoirAuthorityBoundary.snapshot());
        m.put("creditControlBoundary", CreditControlDecisionBoundary.snapshot());
        m.put("liveDecisionAuthorityChanged", DecisionOwnershipFlags.LIVE_DECISION_AUTHORITY_CHANGED);
        m.put("frozenRetired", DecisionOwnershipFlags.FROZEN_RETIRED);
        return m;
    }

    private static Map<String, Object> row(String component, String classification, String note, String wave) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("component", component);
        m.put("classification", classification);
        m.put("note", note);
        m.put("targetWave", wave);
        return m;
    }
}
