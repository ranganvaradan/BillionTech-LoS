package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-7 complete classification of decision-capable components.
 * No path may remain UNKNOWN in the committed inventory.
 */
public final class DecisionAuthorityInventory {

    public enum AuthorityClass {
        POLICY_ELIGIBILITY,
        SCORECARD_SCORING,
        UNDERWRITING_ORCHESTRATION,
        CREDIT_CONTROL_PREPROCESSING,
        LIMIT_CONTROL,
        PRICING_CONTROL,
        WORKFLOW_READINESS,
        MANUAL_OVERRIDE,
        PRODUCT_ROUTING,
        LEGACY_DUPLICATE,
        TEST_ONLY
    }

    public record Entry(
            String component,
            String methodOrRole,
            String decisions,
            AuthorityClass classification,
            boolean liveAuthority,
            String targetOwner,
            String notes
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("component", component);
            m.put("methodOrRole", methodOrRole);
            m.put("decisions", decisions);
            m.put("classification", classification.name());
            m.put("liveAuthority", liveAuthority);
            m.put("targetOwner", targetOwner);
            m.put("notes", notes);
            return m;
        }
    }

    private DecisionAuthorityInventory() {}

    public static List<Entry> all() {
        List<Entry> e = new ArrayList<>();
        e.add(entry("CanonicalPolicyRuntime", "evaluate", "PASS/FAIL/REFER/DI/ERROR",
                AuthorityClass.POLICY_ELIGIBILITY, false, "POLICY", "Target sole eligibility evaluator"));
        e.add(entry("TargetLiveCanonicalPolicyEvaluation", "evaluate", "same as CPR",
                AuthorityClass.POLICY_ELIGIBILITY, false, "POLICY", "Non-cutover entry"));
        e.add(entry("CanonicalUnderwritingOrchestration", "assemble", "APPROVE/REJECT/REFER/DI/ERROR",
                AuthorityClass.UNDERWRITING_ORCHESTRATION, false, "UNDERWRITING", "Target facade Wave-7"));
        e.add(entry("LoanApplicationFlowService", "underwriteApplication", "APPROVED/REJECTED/MANUAL_REVIEW",
                AuthorityClass.UNDERWRITING_ORCHESTRATION, true, "UNDERWRITING", "Current live authority"));
        e.add(entry("LoanApplicationFlowService", "completeManualUnderwritingDecision", "APPROVE/REJECT",
                AuthorityClass.MANUAL_OVERRIDE, true, "MANUAL_OVERRIDE", "CM override"));
        e.add(entry("LoanApplicationFlowService", "applyProcessOverride", "force APPROVED",
                AuthorityClass.MANUAL_OVERRIDE, true, "MANUAL_OVERRIDE", "Process override"));
        e.add(entry("UnderwritingRuleEngine", "evaluateAll/hardRules", "REJECT/APPROVE/MANUAL",
                AuthorityClass.LEGACY_DUPLICATE, true, "POLICY", "Live policy twin — migrate to CPR"));
        e.add(entry("FrozenUnderwritingRuleEngine", "evaluateAll", "same as UW rules",
                AuthorityClass.LEGACY_DUPLICATE, false, "POLICY", "Replay/shadow"));
        e.add(entry("ScorecardPolicyEngine", "hardRules", "REJECT/MANUAL early exit",
                AuthorityClass.POLICY_ELIGIBILITY, true, "POLICY", "Eligibility embedded — Wave-7 shadow flag"));
        e.add(entry("ScorecardPolicyEngine", "bands/ScorecardSafetyScoring", "APPROVE/MANUAL/REJECT by score",
                AuthorityClass.SCORECARD_SCORING, true, "SCORECARD", "Points/weights/bands only target"));
        e.add(entry("FormulaEvaluator", "COMPUTED", "factor values",
                AuthorityClass.SCORECARD_SCORING, true, "SCORECARD", "Preserve"));
        e.add(entry("CreditControlService", "buildEffectiveContext/FOIR prep", "operands only",
                AuthorityClass.CREDIT_CONTROL_PREPROCESSING, true, "CREDIT_CONTROL", "No duplicate policy decision"));
        e.add(entry("CreditControlService", "SCF_MIN_ANNUAL_GST_TURNOVER", "hardcoded min turnover",
                AuthorityClass.LIMIT_CONTROL, true, "LIMIT_CONTROL", "Not policy DSL"));
        e.add(entry("LimitSizingService", "applySanctionCap", "amount cap",
                AuthorityClass.LIMIT_CONTROL, true, "LIMIT_CONTROL", "Preserve"));
        e.add(entry("LimitMethodEngine", "FOIR_LIMIT", "amount outcome",
                AuthorityClass.LIMIT_CONTROL, false, "LIMIT_CONTROL", "Shadow/decision package"));
        e.add(entry("PricingEngine", "compute", "rate floor/cap",
                AuthorityClass.PRICING_CONTROL, false, "PRICING_CONTROL", "Shadow package"));
        e.add(entry("CreditDecisionServiceImpl", "evaluate", "APPROVED/REJECTED + FOIR 0.50",
                AuthorityClass.LEGACY_DUPLICATE, true, "POLICY", "Fallback legacy"));
        e.add(entry("CreditRulesEngine", "evaluate", "AUTO_APPROVED/REJECTED",
                AuthorityClass.LEGACY_DUPLICATE, true, "POLICY", "Product FOIR/DPD legacy"));
        e.add(entry("CreditEnhancementService", "FOIR deviation", "pricing recommendations",
                AuthorityClass.PRICING_CONTROL, true, "PRICING_CONTROL", "Advisory"));
        e.add(entry("DataCompletenessGate", "evaluate", "READY/BLOCK",
                AuthorityClass.WORKFLOW_READINESS, true, "WORKFLOW", "Not approval"));
        e.add(entry("W6EvaluationContextFactory", "build", "facts/asOf",
                AuthorityClass.WORKFLOW_READINESS, true, "WORKFLOW", "Acquisition only"));
        e.add(entry("CustomerCategoryEligibilityService", "match", "category routing",
                AuthorityClass.PRODUCT_ROUTING, true, "PRODUCT_ROUTING", "Not borrower knockout"));
        e.add(entry("AnchorDueDiligenceService", "complete", "APPROVED/REJECTED",
                AuthorityClass.PRODUCT_ROUTING, true, "PRODUCT_ROUTING", "Anchor product path"));
        e.add(entry("ShadowPolicyEngine", "evaluate", "PASS/FAIL/REFER",
                AuthorityClass.POLICY_ELIGIBILITY, false, "POLICY", "Frozen-map shadow"));
        e.add(entry("ShadowDecisionEngine", "recommend", "APPROVE/DECLINE",
                AuthorityClass.UNDERWRITING_ORCHESTRATION, false, "UNDERWRITING", "authoritative=false"));
        e.add(entry("CanonicalBureauRuleEvaluator", "evaluate", "PASS/FAIL",
                AuthorityClass.LEGACY_DUPLICATE, false, "POLICY", "CI domain shadow → CPR"));
        e.add(entry("CanonicalBankingRuleEvaluator", "evaluate", "PASS/FAIL/REFER",
                AuthorityClass.LEGACY_DUPLICATE, false, "POLICY", "CI domain shadow → CPR"));
        e.add(entry("CanonicalGstRuleEvaluator", "evaluate", "PASS/FAIL/REFER",
                AuthorityClass.LEGACY_DUPLICATE, false, "POLICY", "CI domain shadow → CPR"));
        e.add(entry("PolicyStudioTestExperienceService", "evaluateRule", "CPR outcomes",
                AuthorityClass.TEST_ONLY, false, "POLICY", "Uses CPR"));
        e.add(entry("PolicyGraphPolicyTestService", "run", "CPR outcomes",
                AuthorityClass.TEST_ONLY, false, "POLICY", "Uses CPR"));
        e.add(entry("VkycWorkflowService", "KYC outcomes", "block/reject intake",
                AuthorityClass.WORKFLOW_READINESS, true, "WORKFLOW", "Prerequisite not credit score"));
        return List.copyOf(e);
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        long unknown = 0;
        for (Entry e : all()) {
            rows.add(e.toMap());
            if (e.classification() == AuthorityClass.LEGACY_DUPLICATE
                    && e.notes() != null && e.notes().contains("UNKNOWN")) {
                unknown++;
            }
        }
        m.put("entries", rows);
        m.put("entryCount", rows.size());
        m.put("unknownCount", unknown);
        m.put("liveDecisionAuthorityChanged", DecisionOwnershipFlags.LIVE_DECISION_AUTHORITY_CHANGED);
        m.put("wave", "WAVE_7");
        return m;
    }

    private static Entry entry(
            String component, String method, String decisions,
            AuthorityClass cls, boolean live, String target, String notes) {
        return new Entry(component, method, decisions, cls, live, target, notes);
    }
}
