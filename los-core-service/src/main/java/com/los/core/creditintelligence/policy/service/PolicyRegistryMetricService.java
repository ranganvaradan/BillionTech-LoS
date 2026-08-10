package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry metric stubs reading frozen input maps only (deterministic, no live DB).
 */
@Service
public class PolicyRegistryMetricService {

    public Map<String, Object> computeFoir(PolicyEvaluationInput input) {
        BigDecimal obligation = num(input, "metric", "obligation.total_emi")
                .add(num(input, "metric", "obligation.proposed_emi"));
        BigDecimal income = num(input, "metric", "income.eligible_monthly");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metricCode", "FOIR");
        if (income.compareTo(BigDecimal.ZERO) <= 0) {
            out.put("outcome", "DATA_INSUFFICIENT");
            out.put("value", null);
            out.put("dataStatus", "DATA_INSUFFICIENT");
            return out;
        }
        BigDecimal foir = obligation.divide(income, 6, RoundingMode.HALF_UP);
        out.put("outcome", "PASS");
        out.put("value", foir);
        out.put("dataStatus", "AVAILABLE");
        out.put("v", foir);
        return out;
    }

    public Map<String, Object> computeLtv(PolicyEvaluationInput input) {
        BigDecimal loan = num(input, "metric", "loan.amount").max(num(input, "fact", "application.loan_amount"));
        BigDecimal collateral = num(input, "metric", "collateral.value");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metricCode", "LTV");
        if (collateral.compareTo(BigDecimal.ZERO) <= 0 || loan.compareTo(BigDecimal.ZERO) <= 0) {
            out.put("outcome", "DATA_INSUFFICIENT");
            out.put("value", null);
            out.put("dataStatus", "DATA_INSUFFICIENT");
            return out;
        }
        BigDecimal ltv = loan.divide(collateral, 6, RoundingMode.HALF_UP);
        out.put("outcome", "PASS");
        out.put("value", ltv);
        out.put("v", ltv);
        out.put("dataStatus", "AVAILABLE");
        return out;
    }

    public Map<String, Object> computeBankPolicyAdjustedAdb3m(PolicyEvaluationInput input) {
        BigDecimal adb = num(input, "metric", "banking.avg_daily_balance_3m");
        BigDecimal loansDisbursed = num(input, "metric", "banking.loans_disbursed_3m");
        BigDecimal gamingCredits = num(input, "metric", "banking.gaming_credits_3m");
        BigDecimal bulkDeposits = num(input, "metric", "banking.bulk_deposits_excluded_3m");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metricCode", "BANK_POLICY_ADJUSTED_ADB_3M");
        if (!has(input.metrics(), "banking.avg_daily_balance_3m")) {
            out.put("outcome", "DATA_INSUFFICIENT");
            out.put("dataStatus", "DATA_INSUFFICIENT");
            out.put("value", null);
            return out;
        }
        BigDecimal adjusted = adb.subtract(loansDisbursed).subtract(gamingCredits).subtract(bulkDeposits);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            adjusted = BigDecimal.ZERO;
        }
        out.put("outcome", "PASS");
        out.put("value", adjusted);
        out.put("v", adjusted);
        out.put("dataStatus", "AVAILABLE");
        return out;
    }

    public Map<String, Object> computeBureauOverdueMetrics(PolicyEvaluationInput input) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metricCode", "BUREAU_OVERDUE_SUITE");
        out.put("overdueAmount", unwrap(input.metrics().get("bureau.overdue.amount")));
        out.put("overdueAgeDays", unwrap(input.metrics().get("bureau.overdue.age_days")));
        out.put("creditCardOverdue", unwrap(input.metrics().get("bureau.credit_card.overdue_amount")));
        out.put("maxDpd6m", unwrap(input.metrics().get("bureau.max_dpd_6m")));
        boolean missing = !has(input.metrics(), "bureau.overdue.amount")
                && !has(input.metrics(), "bureau.max_dpd_6m");
        out.put("outcome", missing ? "DATA_INSUFFICIENT" : "PASS");
        out.put("dataStatus", missing ? "DATA_INSUFFICIENT" : "AVAILABLE");
        return out;
    }

    private BigDecimal num(PolicyEvaluationInput input, String kind, String path) {
        Object raw = "fact".equals(kind) ? input.facts().get(path) : input.metrics().get(path);
        Object v = unwrap(raw);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Object unwrap(Object raw) {
        if (raw instanceof Map<?, ?> mm) {
            Object status = mm.get("dataStatus");
            if (status != null && ("MISSING".equalsIgnoreCase(String.valueOf(status))
                    || "DATA_INSUFFICIENT".equalsIgnoreCase(String.valueOf(status)))) {
                return null;
            }
            return mm.get("value") != null ? mm.get("value") : mm.get("v");
        }
        return raw;
    }

    private boolean has(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return false;
        }
        Object raw = map.get(key);
        if (raw instanceof Map<?, ?> mm) {
            Object status = mm.get("dataStatus");
            return status == null
                    || (!"MISSING".equalsIgnoreCase(String.valueOf(status))
                    && !"DATA_INSUFFICIENT".equalsIgnoreCase(String.valueOf(status)));
        }
        return true;
    }
}
