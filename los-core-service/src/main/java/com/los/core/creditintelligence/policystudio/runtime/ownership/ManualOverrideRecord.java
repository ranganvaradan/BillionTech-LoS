package com.los.core.creditintelligence.policystudio.runtime.ownership;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual override kept separate from automated policy/scorecard results.
 */
public record ManualOverrideRecord(
        String who,
        Instant when,
        FinalUnderwritingDecision.FinalOutcome originalOutcome,
        FinalUnderwritingDecision.FinalOutcome newOutcome,
        String reason,
        String scope
) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("who", who);
        m.put("when", when == null ? null : when.toString());
        m.put("originalOutcome", originalOutcome == null ? null : originalOutcome.name());
        m.put("newOutcome", newOutcome == null ? null : newOutcome.name());
        m.put("reason", reason);
        m.put("scope", scope);
        m.put("mutatesCanonicalRuleResult", false);
        return m;
    }
}
