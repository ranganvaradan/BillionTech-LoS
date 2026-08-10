package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.domain.OwnershipMatchStatus;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.domain.RuleOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow-only banking rules. Production CreditControl gap defaults remain unchanged.
 */
@Component
public class CanonicalBankingRuleEvaluator {

    public static final String DQ_BANK_STATEMENT_AVAILABLE = "DQ_BANK_STATEMENT_AVAILABLE";
    public static final String DQ_BANK_STATEMENT_COMPLETENESS = "DQ_BANK_STATEMENT_COMPLETENESS";
    public static final String DQ_BANK_ACCOUNT_OWNERSHIP = "DQ_BANK_ACCOUNT_OWNERSHIP";
    public static final String BANK_ABB_MINIMUM = "BANK_ABB_MINIMUM";
    public static final String BANK_MONTHLY_CREDIT_MINIMUM = "BANK_MONTHLY_CREDIT_MINIMUM";
    public static final String BANK_CHEQUE_RETURN_MAX = "BANK_CHEQUE_RETURN_MAX";
    public static final String BANK_NACH_RETURN_MAX = "BANK_NACH_RETURN_MAX";
    public static final String BANK_CASH_DEPOSIT_RATIO = "BANK_CASH_DEPOSIT_RATIO";
    public static final String BANK_NEGATIVE_BALANCE_DAYS = "BANK_NEGATIVE_BALANCE_DAYS";
    public static final String BANK_OD_UTILISATION = "BANK_OD_UTILISATION";
    public static final String BANK_EMI_OBLIGATION_AVAILABLE = "BANK_EMI_OBLIGATION_AVAILABLE";

    public record RuleEvalResult(
            String ruleId,
            String ruleVersion,
            String outcome,
            Object value,
            Object threshold,
            Map<String, Object> versions,
            Map<String, Object> evidence) {
    }

    public List<RuleEvalResult> evaluateAll(
            List<CiBankAccount> accounts,
            Map<String, CiMetricResult> metricsByCode,
            BigDecimal abbMinimum,
            BigDecimal monthlyCreditMinimum,
            int chequeReturnMax3m,
            int nachReturnMax3m,
            double cashDepositRatioWarning,
            double odUtilisationWarningPct) {

        List<RuleEvalResult> out = new ArrayList<>();
        out.add(evaluateDqAvailable(accounts));
        out.add(evaluateCompleteness(metricsByCode));
        out.add(evaluateOwnership(accounts));
        out.add(evaluateAbbMinimum(metricsByCode, abbMinimum));
        out.add(evaluateMonthlyCredit(metricsByCode, monthlyCreditMinimum));
        out.add(evaluateChequeReturn(metricsByCode, chequeReturnMax3m));
        out.add(evaluateNachReturn(metricsByCode, nachReturnMax3m));
        out.add(evaluateCashDepositRatio(metricsByCode, cashDepositRatioWarning));
        out.add(evaluateNegativeBalanceDays(metricsByCode));
        out.add(evaluateOdUtilisation(metricsByCode, odUtilisationWarningPct));
        out.add(evaluateEmiAvailable(metricsByCode));
        return out;
    }

    public RuleEvalResult evaluateDqAvailable(List<CiBankAccount> accounts) {
        Map<String, Object> versions = frozenVersions();
        boolean available = accounts != null && !accounts.isEmpty();
        if (!available) {
            return new RuleEvalResult(DQ_BANK_STATEMENT_AVAILABLE, DQ_BANK_STATEMENT_AVAILABLE + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), false, true, versions,
                    Map.of("reason", "NO_ACCOUNTS"));
        }
        return new RuleEvalResult(DQ_BANK_STATEMENT_AVAILABLE, DQ_BANK_STATEMENT_AVAILABLE + "_V1",
                RuleOutcome.PASS.name(), true, true, versions,
                Map.of("accountCount", accounts.size()));
    }

    public RuleEvalResult evaluateCompleteness(Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(BankingMetricService.COMPLETENESS) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(DQ_BANK_STATEMENT_COMPLETENESS, DQ_BANK_STATEMENT_COMPLETENESS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "METRIC_MISSING"));
        }
        BigDecimal ratio = unwrapBd(m.getValue());
        String outcome = BankingMetricOutcome.PASS.name().equals(m.getOutcome())
                ? RuleOutcome.PASS.name() : RuleOutcome.REFER.name();
        return new RuleEvalResult(DQ_BANK_STATEMENT_COMPLETENESS, DQ_BANK_STATEMENT_COMPLETENESS + "_V1",
                outcome, ratio, null, versions, Map.of("completeness", ratio != null ? ratio.toPlainString() : null));
    }

    public RuleEvalResult evaluateOwnership(List<CiBankAccount> accounts) {
        Map<String, Object> versions = frozenVersions();
        if (accounts == null || accounts.isEmpty()) {
            return new RuleEvalResult(DQ_BANK_ACCOUNT_OWNERSHIP, DQ_BANK_ACCOUNT_OWNERSHIP + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "NO_ACCOUNTS"));
        }
        boolean anyMismatch = accounts.stream()
                .anyMatch(a -> OwnershipMatchStatus.MISMATCH.name().equalsIgnoreCase(a.getHolderNameMatchStatus()));
        boolean allUnknown = accounts.stream()
                .allMatch(a -> OwnershipMatchStatus.UNKNOWN.name().equalsIgnoreCase(a.getHolderNameMatchStatus()));
        if (anyMismatch) {
            return new RuleEvalResult(DQ_BANK_ACCOUNT_OWNERSHIP, DQ_BANK_ACCOUNT_OWNERSHIP + "_V1",
                    RuleOutcome.FAIL.name(), "MISMATCH", "MATCH_OR_PROBABLE", versions,
                    Map.of("status", "MISMATCH"));
        }
        if (allUnknown) {
            return new RuleEvalResult(DQ_BANK_ACCOUNT_OWNERSHIP, DQ_BANK_ACCOUNT_OWNERSHIP + "_V1",
                    RuleOutcome.REFER.name(), "UNKNOWN", "MATCH_OR_PROBABLE", versions,
                    Map.of("status", "UNKNOWN"));
        }
        return new RuleEvalResult(DQ_BANK_ACCOUNT_OWNERSHIP, DQ_BANK_ACCOUNT_OWNERSHIP + "_V1",
                RuleOutcome.PASS.name(), "MATCH", "MATCH_OR_PROBABLE", versions,
                Map.of("status", "OK"));
    }

    public RuleEvalResult evaluateAbbMinimum(Map<String, CiMetricResult> metricsByCode, BigDecimal abbMinimum) {
        Map<String, Object> versions = frozenVersions();
        if (abbMinimum == null) {
            return new RuleEvalResult(BANK_ABB_MINIMUM, BANK_ABB_MINIMUM + "_V1",
                    RuleOutcome.REFER.name(), null, null, versions,
                    Map.of("reason", "THRESHOLD_NOT_CONFIGURED"));
        }
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(BankingMetricService.ADB_3M) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(BANK_ABB_MINIMUM, BANK_ABB_MINIMUM + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, abbMinimum, versions,
                    Map.of("reason", "ADB_INSUFFICIENT"));
        }
        BigDecimal value = unwrapBd(m.getValue());
        if (value == null) {
            return new RuleEvalResult(BANK_ABB_MINIMUM, BANK_ABB_MINIMUM + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, abbMinimum, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = value.compareTo(abbMinimum) >= 0 ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(BANK_ABB_MINIMUM, BANK_ABB_MINIMUM + "_V1",
                outcome, value, abbMinimum, versions,
                Map.of("adb3m", value.toPlainString(), "threshold", abbMinimum.toPlainString()));
    }

    public RuleEvalResult evaluateMonthlyCredit(
            Map<String, CiMetricResult> metricsByCode, BigDecimal monthlyCreditMinimum) {
        Map<String, Object> versions = frozenVersions();
        BigDecimal thr = monthlyCreditMinimum != null ? monthlyCreditMinimum : BigDecimal.ZERO;
        CiMetricResult m = metricsByCode != null
                ? metricsByCode.get(BankingMetricService.AVG_M_6M) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(BANK_MONTHLY_CREDIT_MINIMUM, BANK_MONTHLY_CREDIT_MINIMUM + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, thr, versions,
                    Map.of("reason", "METRIC_INSUFFICIENT"));
        }
        BigDecimal value = unwrapBd(m.getValue());
        if (value == null) {
            return new RuleEvalResult(BANK_MONTHLY_CREDIT_MINIMUM, BANK_MONTHLY_CREDIT_MINIMUM + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, thr, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = value.compareTo(thr) >= 0 ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(BANK_MONTHLY_CREDIT_MINIMUM, BANK_MONTHLY_CREDIT_MINIMUM + "_V1",
                outcome, value, thr, versions, Map.of("avgMonthly6m", value.toPlainString()));
    }

    public RuleEvalResult evaluateChequeReturn(Map<String, CiMetricResult> metricsByCode, int maxAllowed) {
        return countRule(BANK_CHEQUE_RETURN_MAX, metricsByCode, BankingMetricService.CHEQUE_3M, maxAllowed);
    }

    public RuleEvalResult evaluateNachReturn(Map<String, CiMetricResult> metricsByCode, int maxAllowed) {
        return countRule(BANK_NACH_RETURN_MAX, metricsByCode, BankingMetricService.NACH_3M, maxAllowed);
    }

    private RuleEvalResult countRule(
            String ruleId, Map<String, CiMetricResult> metricsByCode, String metricCode, int maxAllowed) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(metricCode) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(ruleId, ruleId + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, maxAllowed, versions,
                    Map.of("reason", "METRIC_INSUFFICIENT"));
        }
        Integer count = unwrapInt(m.getValue());
        if (count == null) {
            return new RuleEvalResult(ruleId, ruleId + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, maxAllowed, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        // Valid zero is PASS
        String outcome = count <= maxAllowed ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(ruleId, ruleId + "_V1",
                outcome, count, maxAllowed, versions, Map.of("count", count, "maxAllowed", maxAllowed));
    }

    public RuleEvalResult evaluateCashDepositRatio(
            Map<String, CiMetricResult> metricsByCode, double warningPct) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null
                ? metricsByCode.get(BankingMetricService.CASH_RATIO_12M) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(BANK_CASH_DEPOSIT_RATIO, BANK_CASH_DEPOSIT_RATIO + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, warningPct, versions,
                    Map.of("reason", "METRIC_INSUFFICIENT"));
        }
        BigDecimal value = unwrapBd(m.getValue());
        if (value == null) {
            return new RuleEvalResult(BANK_CASH_DEPOSIT_RATIO, BANK_CASH_DEPOSIT_RATIO + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, warningPct, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = value.doubleValue() <= warningPct
                ? RuleOutcome.PASS.name() : RuleOutcome.REFER.name();
        return new RuleEvalResult(BANK_CASH_DEPOSIT_RATIO, BANK_CASH_DEPOSIT_RATIO + "_V1",
                outcome, value, warningPct, versions,
                Map.of("ratio", value.toPlainString(), "warningPct", warningPct));
    }

    public RuleEvalResult evaluateNegativeBalanceDays(Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null
                ? metricsByCode.get(BankingMetricService.NEG_DAYS_6M) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(BANK_NEGATIVE_BALANCE_DAYS, BANK_NEGATIVE_BALANCE_DAYS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, 0, versions,
                    Map.of("reason", "METRIC_INSUFFICIENT"));
        }
        Integer days = unwrapInt(m.getValue());
        if (days == null) {
            return new RuleEvalResult(BANK_NEGATIVE_BALANCE_DAYS, BANK_NEGATIVE_BALANCE_DAYS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, 0, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = days == 0 ? RuleOutcome.PASS.name() : RuleOutcome.REFER.name();
        return new RuleEvalResult(BANK_NEGATIVE_BALANCE_DAYS, BANK_NEGATIVE_BALANCE_DAYS + "_V1",
                outcome, days, 0, versions, Map.of("negativeDays", days));
    }

    public RuleEvalResult evaluateOdUtilisation(
            Map<String, CiMetricResult> metricsByCode, double warningPct) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null
                ? metricsByCode.get(BankingMetricService.OD_AVG_6M) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(BANK_OD_UTILISATION, BANK_OD_UTILISATION + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, warningPct, versions,
                    Map.of("reason", "OD_LIMIT_OR_DATA_MISSING"));
        }
        BigDecimal value = unwrapBd(m.getValue());
        if (value == null) {
            return new RuleEvalResult(BANK_OD_UTILISATION, BANK_OD_UTILISATION + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, warningPct, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = value.doubleValue() <= warningPct
                ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(BANK_OD_UTILISATION, BANK_OD_UTILISATION + "_V1",
                outcome, value, warningPct, versions,
                Map.of("avgUtilisationPct", value.toPlainString()));
    }

    public RuleEvalResult evaluateEmiAvailable(Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null
                ? metricsByCode.get(BankingMetricService.MONTHLY_OBL) : null;
        if (m == null || BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(BANK_EMI_OBLIGATION_AVAILABLE, BANK_EMI_OBLIGATION_AVAILABLE + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), false, true, versions,
                    Map.of("reason", "EMI_NOT_DETECTED"));
        }
        if (BankingMetricOutcome.REFER.name().equals(m.getOutcome())) {
            return new RuleEvalResult(BANK_EMI_OBLIGATION_AVAILABLE, BANK_EMI_OBLIGATION_AVAILABLE + "_V1",
                    RuleOutcome.REFER.name(), unwrapBd(m.getValue()), true, versions,
                    Map.of("quality", "PARTIAL_SUMMARY"));
        }
        return new RuleEvalResult(BANK_EMI_OBLIGATION_AVAILABLE, BANK_EMI_OBLIGATION_AVAILABLE + "_V1",
                RuleOutcome.PASS.name(), unwrapBd(m.getValue()), true, versions,
                Map.of("available", true));
    }

    public static Map<String, Object> frozenVersions() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("normalizerVersion", BankingNormalizationService.NORMALIZER_VERSION);
        v.put("metricVersion", BankingMetricService.METRIC_VERSION);
        v.put("classifierVersion", BankingConstantsRef.CLASSIFIER);
        v.put("adbMethod", BankingConstantsRef.ADB);
        v.put("turnoverMethod", BankingConstantsRef.TURNOVER);
        v.put("emiMethod", BankingConstantsRef.EMI);
        return v;
    }

    /** Local refs to avoid circular import noise in static block. */
    private static final class BankingConstantsRef {
        static final String CLASSIFIER = com.los.core.creditintelligence.banking.domain.BankingConstants.BANK_TXN_CLASSIFIER_V1;
        static final String ADB = com.los.core.creditintelligence.banking.domain.BankingConstants.BANK_AVERAGE_DAILY_BALANCE_V1;
        static final String TURNOVER = com.los.core.creditintelligence.banking.domain.BankingConstants.ADJUSTED_BANKING_TURNOVER_V1;
        static final String EMI = com.los.core.creditintelligence.banking.domain.BankingConstants.BANK_EMI_DETECTION_V1;
    }

    static Integer unwrapInt(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        Object raw = value.get("v");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return new BigDecimal(String.valueOf(raw)).intValue();
        } catch (Exception e) {
            return null;
        }
    }

    static BigDecimal unwrapBd(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        Object raw = value.get("v");
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }
}
