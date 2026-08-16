package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CreditControl branch classification (Wave-7). Prep retained; duplicate policy decisions shadowed.
 */
public final class CreditControlDecisionBoundary {

    public enum BranchClass {
        FACT_PREPARATION,
        POLICY_DECISION,
        LIMIT_CONTROL,
        PRICING_CONTROL,
        LEGACY_DEFAULT,
        DEMO_FALLBACK
    }

    public record Branch(String name, BranchClass classification, String action, String note) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("classification", classification.name());
            m.put("action", action);
            m.put("note", note);
            return m;
        }
    }

    private CreditControlDecisionBoundary() {}

    public static List<Branch> branches() {
        return List.of(
                new Branch("buildEffectiveContext", BranchClass.FACT_PREPARATION,
                        "RETAIN", "Prepare EffectiveUnderwritingContext"),
                new Branch("FOIR compute obl/inc → percent", BranchClass.FACT_PREPARATION,
                        "RETAIN_PREP", "Policy owns threshold"),
                new Branch("DEMO_DEFAULT_FOIR_RATIO 0.25", BranchClass.DEMO_FALLBACK,
                        "SHADOW_NOT_DECISION_TRUTH", "Provenance DEMO_DEFAULT; orchestration refuses"),
                new Branch("GAP_DEFAULT_FOIR_PERCENT 25", BranchClass.LEGACY_DEFAULT,
                        "SHADOW_NOT_DECISION_TRUTH", "Provenance GAP_DEFAULT; orchestration refuses"),
                new Branch("DEMO income/obligation/bureau", BranchClass.DEMO_FALLBACK,
                        "SHADOW_NOT_DECISION_TRUTH", "May prep map; not approval truth"),
                new Branch("SCF_MIN_ANNUAL_GST_TURNOVER", BranchClass.LIMIT_CONTROL,
                        "RETAIN_AS_LIMIT", "Not Policy DSL"),
                new Branch("AGE into scorecard", BranchClass.FACT_PREPARATION,
                        "RETAIN_PREP", "Age knockout → Policy"),
                new Branch("applyManualKycPassOnProcessOverride", BranchClass.FACT_PREPARATION,
                        "RETAIN", "Supports MANUAL_OVERRIDE path")
        );
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int policyDup = 0;
        int disabledOrShadowed = 0;
        int demoFound = 0;
        for (Branch b : branches()) {
            rows.add(b.toMap());
            if (b.classification() == BranchClass.POLICY_DECISION) policyDup++;
            if (b.action().contains("SHADOW") || b.action().contains("NOT_DECISION")) {
                disabledOrShadowed++;
            }
            if (b.classification() == BranchClass.DEMO_FALLBACK
                    || b.classification() == BranchClass.LEGACY_DEFAULT) {
                demoFound++;
            }
        }
        m.put("branches", rows);
        m.put("creditControlPolicyDuplicatesFound", policyDup);
        m.put("creditControlPolicyDuplicatesDisabledOrShadowed", disabledOrShadowed);
        m.put("demoDefaultDecisionPathsFound", demoFound);
        m.put("demoDefaultDecisionPathsRemaining",
                "Emission with provenance may remain; FinalUnderwritingDecision refuses as truth");
        return m;
    }
}
