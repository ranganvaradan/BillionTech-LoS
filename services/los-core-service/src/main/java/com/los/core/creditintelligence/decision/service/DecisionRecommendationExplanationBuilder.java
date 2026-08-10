package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Human-readable deterministic recommendation explanation (§29).
 */
@Service
public class DecisionRecommendationExplanationBuilder {

    public Map<String, Object> build(CiCreditRecommendation rec) {
        return build(rec, Map.of());
    }

    public Map<String, Object> build(CiCreditRecommendation rec, Map<String, Object> reasonGraph) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (rec == null) {
            return out;
        }
        out.put("recommendationId", rec.getId());
        out.put("outcome", rec.getRecommendationOutcome());
        out.put("deterministicDecisionHash", rec.getDeterministicDecisionHash());
        out.put("shadowOnly", true);
        out.put("authoritative", false);
        out.put("reasonGraph", reasonGraph);

        List<String> lines = new ArrayList<>();
        lines.add("Recommended: " + nullSafe(rec.getRecommendationOutcome()));
        lines.add("");
        lines.add("Amount:");
        lines.add(formatMoney(rec.getRecommendedAmount()) + " vs requested (see dimensions)");
        Object limitDim = rec.getDimensions() == null ? null : rec.getDimensions().get("Limit");
        if (limitDim instanceof Map<?, ?> lm) {
            lines.add("Reason:");
            Object method = lm.get("selectedLimitMethod");
            Object eligible = lm.get("selectedEligibleAmount");
            if (method != null) {
                lines.add(method + "-based capacity limits eligible amount to " + formatMoney(asBd(eligible)) + ".");
            }
        }
        lines.add("");
        lines.add("Tenure:");
        lines.add(rec.getRecommendedTenureMonths() == null ? "n/a" : rec.getRecommendedTenureMonths() + " months");
        lines.add("");
        lines.add("Pricing:");
        if (rec.getRecommendedFinalRate() != null) {
            lines.add(formatPct(rec.getRecommendedFinalRate()));
            lines.add("Base: " + formatPct(rec.getRecommendedBaseRate()));
            lines.add("Risk grade premium: " + formatPct(rec.getRecommendedRiskPremium()));
        } else {
            lines.add("n/a");
        }
        lines.add("");
        lines.add("Conditions:");
        if (rec.getConditionsPrecedent() == null || rec.getConditionsPrecedent().isEmpty()) {
            lines.add("(none)");
        } else {
            int i = 1;
            for (Object c : rec.getConditionsPrecedent()) {
                if (c instanceof Map<?, ?> cm) {
                    Object desc = cm.get("description");
                    if (desc == null) {
                        desc = cm.get("code");
                    }
                    lines.add(i + ". " + desc);
                } else {
                    lines.add(i + ". " + c);
                }
                i++;
            }
        }
        lines.add("");
        lines.add("Authority: " + nullSafe(rec.getApprovalAuthorityLevel()));
        lines.add("Human review required: true");
        lines.add("Authoritative: false");

        out.put("humanReadable", String.join("\n", lines));
        out.put("narrativeLines", lines);
        out.put("dimensions", rec.getDimensions());
        out.put("reasonCodes", rec.getReasonCodes());
        return out;
    }

    private String formatMoney(BigDecimal v) {
        if (v == null) return "n/a";
        return "₹" + v.toPlainString();
    }

    private String formatPct(BigDecimal rate) {
        if (rate == null) return "n/a";
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private BigDecimal asBd(Object o) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    private String nullSafe(Object o) {
        return o == null ? "n/a" : String.valueOf(o);
    }
}
