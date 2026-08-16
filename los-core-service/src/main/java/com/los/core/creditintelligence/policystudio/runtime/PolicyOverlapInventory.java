package com.los.core.creditintelligence.policystudio.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-5 Scorecard / CreditControl overlap inventory (ownership deferred to Wave 7).
 */
public final class PolicyOverlapInventory {

    private PolicyOverlapInventory() {}

    public static List<Map<String, Object>> scorecardOverlaps() {
        return List.of(
                row("ScorecardPolicyEngine.hardRules", "POLICY_ELIGIBILITY",
                        "Hard rules embedded in scorecard can REJECT before scoring",
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
                row("CreditControlService.buildEffectiveContext", "POST_POLICY_CONTROL",
                        "Prepares EffectiveUnderwritingContext / scorecard map",
                        "WAVE_7"),
                row("CreditControlService.FOIR_placeholders", "DUPLICATE_POLICY_LOGIC",
                        "FOIR / obligation prep may overlap policy operands",
                        "WAVE_7"),
                row("CreditControlService.SCF_MIN_ANNUAL_GST_TURNOVER", "LIMIT_CONTROL",
                        "Hardcoded SCF minimum turnover",
                        "WAVE_7"),
                row("ApplicationScorecardParameterResolver.age", "WORKFLOW_CONTROL",
                        "Uses LocalDate.now() for age",
                        "WAVE_6_CLOCK")
        );
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scorecardPolicyOverlaps", scorecardOverlaps());
        m.put("creditControlPolicyOverlaps", creditControlOverlaps());
        m.put("wave5MigratesOwnership", false);
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
