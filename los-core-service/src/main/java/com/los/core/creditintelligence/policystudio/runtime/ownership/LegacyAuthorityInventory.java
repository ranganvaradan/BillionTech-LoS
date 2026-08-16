package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-10 disposition of every legacy/parallel authority.
 * Full retirement is NOT claimed while LIVE_CUTOVER_READY = NO.
 */
public final class LegacyAuthorityInventory {

    public enum Disposition {
        KEEP_PRIMARY_LIVE,
        KEEP_ROLLBACK,
        KEEP_AS_FACADE,
        KEEP_TEST_ONLY,
        KEEP_AUTHORING,
        NEUTRALIZED_RUNTIME,
        DEPRECATED_EXECUTION,
        RETIRE_WHEN_CUTOVER,
        RETIRED_THIS_WAVE
    }

    private LegacyAuthorityInventory() {}

    public static List<Map<String, Object>> inventory() {
        List<Map<String, Object>> rows = new ArrayList<>();
        row(rows, "UnderwritingRuleEngine", Disposition.KEEP_PRIMARY_LIVE,
                "Live LoanApplicationFlowService", "CanonicalUnderwritingOrchestration", true, "CRITICAL");
        row(rows, "FrozenUnderwritingRuleEngine", Disposition.KEEP_ROLLBACK,
                "Shadow/replay", "CPR", true, "HIGH");
        row(rows, "ShadowPolicyEngine", Disposition.KEEP_AS_FACADE,
                "Staging/cutover tools", "CPR shadow", false, "MEDIUM");
        row(rows, "PolicyBureauMetricService", Disposition.DEPRECATED_EXECUTION,
                "Studio/tests only — not CPES producer", "BuiltInBureau / CPES", false, "LOW");
        row(rows, "PolicyGraphPolicyTestService", Disposition.KEEP_AS_FACADE,
                "Already CPR+CPES", "PolicyStudioTestExperience", false, "LOW");
        row(rows, "runtimeFactAliases", Disposition.KEEP_AS_FACADE,
                "TRUE_COMPAT only via CanonicalCompatibilityRegistry", "CanonicalCompatibilityRegistry", false, "LOW");
        row(rows, "CanonicalCompatibilityRegistry", Disposition.KEEP_AS_FACADE,
                "Single alias authority", "—", false, "LOW");
        row(rows, "BuiltInBanking stagingFixture", Disposition.KEEP_TEST_ONLY,
                "POLICY_TEST mode gate", "live facts", false, "LOW");
        row(rows, "CreditControlService FOIR prep", Disposition.KEEP_PRIMARY_LIVE,
                "Fact prep on live path", "FACT_PREP only on CANONICAL", true, "CRITICAL");
        row(rows, "CreditDecisionServiceImpl FOIR", Disposition.RETIRE_WHEN_CUTOVER,
                "Fallback decision twin", "CPR Policy FOIR", true, "CRITICAL");
        row(rows, "ScorecardPolicyEngine hardRules", Disposition.KEEP_PRIMARY_LIVE,
                "Flag default false = live", "Policy eligibility + shadowOnly flag", true, "HIGH");
        row(rows, "GacatParameterReadinessProjection", Disposition.NEUTRALIZED_RUNTIME,
                "D&P Advanced; overall not live cert", "CanonicalParameterTruthProjection", false, "MEDIUM");
        row(rows, "DataParametersCapabilitySemantics", Disposition.KEEP_AS_FACADE,
                "liveUse overridden by Wave-9 truth", "CanonicalParameterTruthProjection", false, "LOW");
        row(rows, "catalogue.implemented/production_ready", Disposition.NEUTRALIZED_RUNTIME,
                "Display/migration metadata only after Wave-10 rewires", "Certification + CPES", false, "MEDIUM");
        row(rows, "latestFor()", Disposition.KEEP_AUTHORING,
                "Authoring current; target-live uses PinnedArtifactSelection", "PinnedArtifactSelection", true, "HIGH");
        row(rows, "EvaluationContextFactory wall-clock", Disposition.NEUTRALIZED_RUNTIME,
                "Requires asOf or EvaluationClock", "explicit asOf", false, "MEDIUM");
        row(rows, "LiveDecisionAuthority", Disposition.KEEP_PRIMARY_LIVE,
                "Default LEGACY_FROZEN", "flip to CANONICAL when cutover ready", true, "CRITICAL");
        return rows;
    }

    public static Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wave", 10);
        m.put("LIVE_CUTOVER_READY", false);
        m.put("fullRetirementClaimed", false);
        m.put("authorities", inventory());
        m.put("count", inventory().size());
        return m;
    }

    private static void row(
            List<Map<String, Object>> rows,
            String component,
            Disposition disposition,
            String callers,
            String replacement,
            boolean rollbackRequired,
            String risk) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("component", component);
        r.put("disposition", disposition.name());
        r.put("callers", callers);
        r.put("targetReplacement", replacement);
        r.put("safeToRetireNow", disposition == Disposition.RETIRED_THIS_WAVE
                || disposition == Disposition.DEPRECATED_EXECUTION
                || disposition == Disposition.KEEP_TEST_ONLY
                || disposition == Disposition.NEUTRALIZED_RUNTIME);
        r.put("rollbackRequired", rollbackRequired);
        r.put("risk", risk);
        rows.add(r);
    }
}
