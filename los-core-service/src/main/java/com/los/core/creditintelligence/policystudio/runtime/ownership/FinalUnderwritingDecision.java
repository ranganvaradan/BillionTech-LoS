package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-7 target underwriting result contract (in-memory; no DB migration).
 */
public record FinalUnderwritingDecision(
        String applicationId,
        LocalDate evaluationAsOf,
        Map<String, Object> policyResult,
        Map<String, Object> scorecardResult,
        Map<String, Object> limitResult,
        Map<String, Object> pricingResult,
        ManualOverrideRecord manualOverride,
        FinalOutcome finalOutcome,
        List<String> reasonCodes,
        Map<String, Object> provenance
) {
    public enum FinalOutcome {
        APPROVE,
        REJECT,
        REFER,
        DATA_INSUFFICIENT,
        ERROR,
        /** Operational/governance block — not an adverse credit REJECT. */
        LIVE_BLOCKED
    }

    public FinalUnderwritingDecision {
        if (policyResult == null) policyResult = Map.of();
        if (scorecardResult == null) scorecardResult = Map.of();
        if (limitResult == null) limitResult = Map.of();
        if (pricingResult == null) pricingResult = Map.of();
        if (reasonCodes == null) reasonCodes = List.of();
        if (provenance == null) provenance = Map.of();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", applicationId);
        m.put("evaluationAsOf", evaluationAsOf == null ? null : evaluationAsOf.toString());
        m.put("policyResult", policyResult);
        m.put("scorecardResult", scorecardResult);
        m.put("limitResult", limitResult);
        m.put("pricingResult", pricingResult);
        m.put("manualOverride", manualOverride == null ? null : manualOverride.toMap());
        m.put("finalOutcome", finalOutcome == null ? null : finalOutcome.name());
        m.put("reasonCodes", reasonCodes);
        m.put("provenance", provenance);
        m.put("liveDecisionAuthorityChanged", DecisionOwnershipFlags.LIVE_DECISION_AUTHORITY_CHANGED);
        m.put("contract", "FinalUnderwritingDecision");
        return m;
    }
}
