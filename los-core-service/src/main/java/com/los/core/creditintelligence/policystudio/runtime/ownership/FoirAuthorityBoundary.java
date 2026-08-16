package com.los.core.creditintelligence.policystudio.runtime.ownership;

import com.los.core.service.underwriting.ScorecardValueProvenance;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FOIR single-authority boundary (Wave-7).
 * CreditControl prepares ratio/percent with provenance.
 * Policy (CanonicalPolicyRuntime) owns lender eligibility threshold.
 * Demo/gap defaults are never decision truth when flags enforce it.
 */
public final class FoirAuthorityBoundary {

    public static final String CANONICAL_PARAMETER = "obligation.ratio";
    public static final String SCORECARD_KEY = "OBLIGATION_RATIO";
    /** CreditControl stores percent (ratio × 100). Catalogue FOIR_MAX also uses percent consts. */
    public static final String UNIT_PERCENT = "PERCENT";
    public static final String UNIT_RATIO = "RATIO_0_1";

    public static final String AUTHORITY_BEFORE =
            "MULTI: CreditControl demo/gap + Scorecard hardRules + UW rules + CreditDecisionServiceImpl(0.50 ratio) + CreditRulesEngine(product %)";
    public static final String AUTHORITY_AFTER =
            "CreditControl=FACT_PREPARATION; Policy/CPR=ELIGIBILITY threshold; LimitMethodEngine=amount only; legacy engines=LEGACY_DUPLICATE";

    private FoirAuthorityBoundary() {}

    public record PreparedFoir(
            BigDecimal value,
            String unit,
            String provenance,
            boolean authoritativeForDecision,
            String note
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", value);
            m.put("unit", unit);
            m.put("provenance", provenance);
            m.put("authoritativeForDecision", authoritativeForDecision);
            m.put("canonicalParameter", CANONICAL_PARAMETER);
            m.put("note", note);
            return m;
        }
    }

    /** Classify a scorecard FOIR cell for decision honesty. */
    public static PreparedFoir classifyScorecardFoir(BigDecimal percentValue, String provenance) {
        boolean nonAuth = DecisionOwnershipFlags.demoDefaultsNotDecisionTruth()
                && ScorecardValueProvenance.isNonAuthoritative(provenance);
        return new PreparedFoir(
                percentValue,
                UNIT_PERCENT,
                provenance,
                !nonAuth && percentValue != null,
                nonAuth
                        ? "Demo/gap/simulated FOIR is not decision truth — Policy must see DATA_INSUFFICIENT"
                        : "Prepared percent for Policy threshold evaluation");
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("authorityBefore", AUTHORITY_BEFORE);
        m.put("authorityAfter", AUTHORITY_AFTER);
        m.put("canonicalParameter", CANONICAL_PARAMETER);
        m.put("scorecardKey", SCORECARD_KEY);
        m.put("creditControlRole", "FACT_PREPARATION");
        m.put("policyRole", "POLICY_ELIGIBILITY");
        m.put("demoDefaultPathsRemaining",
                "CreditControl may still emit DEMO_DEFAULT/GAP_DEFAULT with provenance; orchestration refuses decision truth");
        m.put("unitRisk", "Legacy CreditDecisionServiceImpl uses ratio 0–1; CreditControl stores percent — Policy must use percent consistently");
        return m;
    }
}
