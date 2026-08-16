package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One business condition → one target owner (Wave-7).
 */
public final class DuplicateBusinessConditionInventory {

    public enum DuplicationClass {
        EXACT_DUPLICATE,
        OVERLAPPING,
        DIFFERENT_PURPOSE,
        LEGACY,
        NOT_DUPLICATE
    }

    public record Condition(
            String businessCondition,
            String canonicalParameters,
            String currentPolicy,
            String currentScorecard,
            String currentCreditControl,
            String currentFrozen,
            String currentWorkflow,
            String targetOwner,
            DuplicationClass duplication
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("businessCondition", businessCondition);
            m.put("canonicalParameters", canonicalParameters);
            m.put("currentPolicy", currentPolicy);
            m.put("currentScorecard", currentScorecard);
            m.put("currentCreditControl", currentCreditControl);
            m.put("currentFrozen", currentFrozen);
            m.put("currentWorkflow", currentWorkflow);
            m.put("targetOwner", targetOwner);
            m.put("duplication", duplication.name());
            return m;
        }
    }

    private DuplicateBusinessConditionInventory() {}

    public static List<Condition> all() {
        List<Condition> c = new ArrayList<>();
        c.add(cond("FOIR / obligation ratio", "obligation.ratio",
                "Catalogue FIN.FOIR_MAX / CPR", "hardRules/bands on OBLIGATION_RATIO",
                "computes percent + DEMO/GAP defaults", "hardRules twin", "n/a",
                "POLICY", DuplicationClass.EXACT_DUPLICATE));
        c.add(cond("Minimum bureau score", "bureau.score",
                "Policy DSL / CPR", "hardRules + bands", "demo bureau default",
                "minBureauScore + hardRules", "n/a",
                "POLICY", DuplicationClass.EXACT_DUPLICATE));
        c.add(cond("Max DPD", "bureau.max_dpd_6m / 12m / 24m",
                "Policy DSL", "MAX_DPD_* hardRules", "provenance only",
                "hardRules", "n/a",
                "POLICY", DuplicationClass.OVERLAPPING));
        c.add(cond("Write-off", "bureau.accounts.writeoff_non_cc",
                "Policy DSL", "possible hardRule", "none", "possible", "n/a",
                "POLICY", DuplicationClass.OVERLAPPING));
        c.add(cond("Settled accounts", "bureau.settled_account_count",
                "Policy DSL", "possible hardRule", "none", "possible", "n/a",
                "POLICY", DuplicationClass.OVERLAPPING));
        c.add(cond("Applicant age", "application.age / AGE",
                "Policy DSL", "AGE hardRule", "AGE into scorecard map", "possible", "n/a",
                "POLICY", DuplicationClass.EXACT_DUPLICATE));
        c.add(cond("Income", "application.declared_income / MONTHLY_INCOME",
                "Policy threshold if any", "factor scoring", "prep + demo/gap", "possible", "n/a",
                "CREDIT_CONTROL prep + POLICY if knockout", DuplicationClass.DIFFERENT_PURPOSE));
        c.add(cond("Obligation", "bureau.total_monthly_obligation",
                "Policy if knockout", "factor", "prep", "possible", "n/a",
                "CREDIT_CONTROL prep + POLICY if knockout", DuplicationClass.DIFFERENT_PURPOSE));
        c.add(cond("ADB / ABB", "banking.avg_daily_balance_3m",
                "Policy DSL", "factor/hardRule", "gap default ABB", "possible", "n/a",
                "POLICY", DuplicationClass.OVERLAPPING));
        c.add(cond("EMI bounce", "banking.emi_bounce_count_3m",
                "Policy DSL", "possible", "CHEQUE_BOUNCES + gap", "possible", "n/a",
                "POLICY", DuplicationClass.OVERLAPPING));
        c.add(cond("Credit utilisation", "bureau utilisation IDs",
                "Policy if defined", "factor", "none", "possible", "n/a",
                "POLICY", DuplicationClass.NOT_DUPLICATE));
        c.add(cond("Product / program eligibility", "routing dims",
                "n/a (routing)", "n/a", "n/a", "n/a", "CustomerCategory / Anchor",
                "PRODUCT_ROUTING", DuplicationClass.DIFFERENT_PURPOSE));
        c.add(cond("Score hard knockout", "legacy scorecard hardRules",
                "should be Policy", "hardRules REJECT", "n/a", "twin", "n/a",
                "POLICY", DuplicationClass.EXACT_DUPLICATE));
        c.add(cond("Score bands / points", "factor values via CPES",
                "must not own", "bands/weights", "n/a", "embedded scorecardRules", "n/a",
                "SCORECARD", DuplicationClass.NOT_DUPLICATE));
        c.add(cond("Data completeness", "required sources",
                "onMissing DI", "missing→MANUAL", "n/a", "n/a", "DataCompletenessGate / W6",
                "WORKFLOW", DuplicationClass.DIFFERENT_PURPOSE));
        c.add(cond("Max loan amount", "application.requested_amount",
                "optional policy", "n/a", "n/a", "maxLoanAmount constraint", "n/a",
                "LIMIT_CONTROL", DuplicationClass.OVERLAPPING));
        c.add(cond("SCF GST min turnover", "gst turnover",
                "optional policy", "n/a", "SCF_MIN hardcoded", "n/a", "n/a",
                "LIMIT_CONTROL", DuplicationClass.LEGACY));
        return List.copyOf(c);
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Condition c : all()) rows.add(c.toMap());
        m.put("conditions", rows);
        m.put("conditionCount", rows.size());
        long exact = all().stream().filter(x -> x.duplication() == DuplicationClass.EXACT_DUPLICATE).count();
        m.put("exactDuplicateCount", exact);
        return m;
    }

    private static Condition cond(
            String name, String canon, String pol, String sc, String cc, String fr, String wf,
            String owner, DuplicationClass dup) {
        return new Condition(name, canon, pol, sc, cc, fr, wf, owner, dup);
    }
}
