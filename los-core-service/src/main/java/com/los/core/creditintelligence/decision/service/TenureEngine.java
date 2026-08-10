package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TenureEngine {

    public record TenureResult(
            Integer requestedTenure,
            Integer minMonths,
            Integer maxMonths,
            Integer recommendedTenure,
            String reason,
            List<String> reasonCodes,
            Map<String, Object> detail
    ) {}

    public TenureResult compute(Map<String, Object> tenureStrategy, DecisionRuntimeInput input) {
        Map<String, Object> s = tenureStrategy == null ? Map.of() : tenureStrategy;
        int min = intVal(s.get("minMonths"), 6);
        int max = intVal(s.get("maxMonths"), 36);
        int def = intVal(s.get("defaultMonths"), 18);
        Integer requested = input.requestedTenureMonths();

        List<String> reasons = new ArrayList<>();
        int recommended;
        String reason;
        if (requested == null) {
            recommended = clamp(def, min, max);
            reason = "DEFAULT_TENURE";
            reasons.add("TENURE_DEFAULT");
        } else if (requested < min) {
            recommended = min;
            reason = "BELOW_MIN_CLAMPED";
            reasons.add("TENURE_CLAMPED_MIN");
        } else if (requested > max) {
            recommended = max;
            reason = "ABOVE_MAX_CLAMPED";
            reasons.add("TENURE_CLAMPED_MAX");
        } else {
            recommended = requested;
            reason = "REQUESTED_WITHIN_RANGE";
            reasons.add("TENURE_ACCEPTED");
        }

        // Soft nudge for weak risk grade toward default if longer than default
        String grade = DecisionValueHelper.str(input.scoreResult().get("grade"));
        if (grade != null && ("D".equalsIgnoreCase(grade) || "DATA_INSUFFICIENT".equalsIgnoreCase(grade))
                && recommended > def) {
            recommended = def;
            reason = "RISK_GRADE_TENURE_CAP";
            reasons.add("TENURE_RISK_GRADE_CAP");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requestedTenure", requested);
        detail.put("eligibleTenureRange", Map.of("min", min, "max", max));
        detail.put("recommendedTenure", recommended);
        detail.put("reason", reason);
        return new TenureResult(requested, min, max, recommended, reason, reasons, detail);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int intVal(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }
}
