package com.los.core.creditintelligence.policystudio.lineage;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves Capability → Metric → existing computation/helper → source data.
 * Does not duplicate metric calculations.
 */
@Service
public class PolicyMetricLineageService {

    public static final String AVAILABLE_AUTOMATICALLY = "AVAILABLE_AUTOMATICALLY";
    public static final String DERIVABLE_FROM_AVAILABLE_DATA = "DERIVABLE_FROM_AVAILABLE_DATA";
    public static final String MANUAL_INPUT_AVAILABLE = "MANUAL_INPUT_AVAILABLE";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String NEEDS_CONFIGURATION = "NEEDS_CONFIGURATION";

    public PolicyMetricLineage resolve(String metricCodeOrAlias) {
        if (metricCodeOrAlias == null || metricCodeOrAlias.isBlank()) {
            return null;
        }
        String code = metricCodeOrAlias.trim();
        String lower = code.toLowerCase(Locale.ROOT);

        // Settlement ↔ QR helper binding (SMART SWITCH)
        if (lower.contains("settlement.count_monthly") || lower.contains("qr_settlement.average_monthly_count")) {
            return PolicyMetricLineage.of(
                    "banking.settlement.count_monthly_avg_3m",
                    "Average monthly settlements",
                    "Bank statement",
                    DERIVABLE_FROM_AVAILABLE_DATA,
                    "Number of qualifying QR settlement credits during the trailing 3 months ÷ 3",
                    "Last 3 months (TRAILING_3M)",
                    "If QR taxonomy cannot classify settlements → DATA_INSUFFICIENT (not zero)",
                    "QR_SETTLEMENT",
                    "banking.qr_settlement.average_monthly_count_3m");
        }
        if (lower.contains("settlement.avg_daily") || lower.contains("qr_settlement.average_daily")) {
            return PolicyMetricLineage.of(
                    "banking.settlement.avg_daily_3m",
                    "Average daily settlements",
                    "Bank statement",
                    DERIVABLE_FROM_AVAILABLE_DATA,
                    "Sum of qualifying QR settlement credit amounts during the trailing 3 months ÷ 90",
                    "Last 3 months (TRAILING_3M)",
                    "If QR taxonomy cannot classify settlements → DATA_INSUFFICIENT (not zero)",
                    "QR_SETTLEMENT",
                    "banking.qr_settlement.average_daily_3m");
        }
        if (lower.contains("avg_daily_balance") || lower.equals("banking.avg_daily_balance_3m")) {
            return PolicyMetricLineage.of(
                    "banking.avg_daily_balance_3m",
                    "Average daily balance",
                    "Bank statement",
                    AVAILABLE_AUTOMATICALLY,
                    "End-of-day balance carry-forward average over trailing 3 months",
                    "Last 3 months",
                    "Continuity / coverage gaps → DATA_INSUFFICIENT",
                    null,
                    "banking.avg_daily_balance_3m");
        }
        if (lower.contains("transaction_count") && lower.contains("average_monthly")) {
            return PolicyMetricLineage.of(
                    "banking.transaction_count.average_monthly_3m",
                    "Average monthly transactions",
                    "Bank statement",
                    DERIVABLE_FROM_AVAILABLE_DATA,
                    "Transaction count in trailing 3 months ÷ 3",
                    "Last 3 months",
                    "Insufficient classification coverage → DATA_INSUFFICIENT",
                    null,
                    "banking.average_monthly_transaction_count_3m");
        }
        if (lower.contains("inward_return") || lower.contains("inward_cheque_return")) {
            return PolicyMetricLineage.of(
                    "banking.inward_return.count_3m",
                    "Inward cheque / ECS / ENACH returns",
                    "Bank statement",
                    AVAILABLE_AUTOMATICALLY,
                    "Count (and ratio) of CHEQUE_RETURN / inward-return classified transactions in trailing 3 months",
                    "Last 3 months",
                    "No transactions → DATA_INSUFFICIENT for ratio",
                    "CHEQUE_RETURN",
                    "banking.inward_cheque_return_count_3m");
        }
        if (lower.contains("cheque_return") || lower.contains("cheque_bounce")) {
            return PolicyMetricLineage.of(
                    "banking.cheque_return_count_3m",
                    "Cheque returns",
                    "Bank statement",
                    AVAILABLE_AUTOMATICALLY,
                    "Count of CHEQUE_RETURN classified transactions in trailing window",
                    "Last 3 months (typical)",
                    "Valid zero count is PASS when computed",
                    "CHEQUE_RETURN",
                    "banking.cheque_return_count_3m");
        }
        if (lower.contains("bureau.score") || lower.equals("bureau_score")) {
            return PolicyMetricLineage.of(
                    "bureau.score",
                    "Bureau score",
                    "Bureau report",
                    AVAILABLE_AUTOMATICALLY,
                    "Provider bureau score on the application report",
                    "Point in time",
                    "Missing score → DATA_INSUFFICIENT / NTC path if configured",
                    null,
                    "bureau.score");
        }
        if (lower.contains("live_unsecured")) {
            return PolicyMetricLineage.of(
                    "bureau.live_unsecured_loan_count",
                    "Live unsecured loans",
                    "Bureau report",
                    AVAILABLE_AUTOMATICALLY,
                    "Count of live unsecured tradelines per BureauMetricService definition",
                    "Point in time",
                    "DATA_INSUFFICIENT when tradeline classification incomplete",
                    null,
                    "bureau.live_unsecured_loan_count");
        }
        if (lower.contains("inquir") || lower.contains("enquir")) {
            return PolicyMetricLineage.of(
                    "bureau.recent_inquiries_90d",
                    "Bureau enquiries",
                    "Bureau report",
                    AVAILABLE_AUTOMATICALLY,
                    "Enquiry count in configured window",
                    "Window from policy (often 90d / 3M)",
                    "Partial enquiry age fields → DATA_INSUFFICIENT",
                    null,
                    "bureau.recent_inquiries_90d");
        }

        return PolicyMetricLineage.of(
                code,
                friendlyName(code),
                inferSource(code),
                AVAILABLE_AUTOMATICALLY,
                "See Technical Details for metric identifier",
                null,
                "Follows metric service outcome (PASS / DATA_INSUFFICIENT)",
                null,
                code);
    }

    public PolicyMetricLineage resolveFromInputs(List<String> inputs, String systemRuleId) {
        if (inputs != null) {
            for (String in : inputs) {
                if (in == null) continue;
                String l = in.toLowerCase(Locale.ROOT);
                if (l.contains("settlement") || l.contains("qr_settlement")
                        || l.contains("avg_daily_balance") || l.contains("transaction_count")
                        || l.contains("inward") || l.contains("cheque") || l.contains("bureau")) {
                    return resolve(in);
                }
            }
            if (!inputs.isEmpty()) {
                return resolve(inputs.get(0));
            }
        }
        if (systemRuleId != null && systemRuleId.toUpperCase(Locale.ROOT).contains("SETTLEMENT")) {
            return resolve("banking.settlement.count_monthly_avg_3m");
        }
        return null;
    }

    public Map<String, Object> howCalculated(PolicyMetricLineage lineage) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (lineage == null) {
            return m;
        }
        m.put("source", lineage.source());
        m.put("metric", lineage.businessMetricName());
        m.put("calculation", lineage.derivation());
        m.put("assessmentPeriod", lineage.window());
        m.put("dataAvailability", friendlyAvailability(lineage.availability()));
        m.put("missingData", lineage.missingDataBehaviour());
        if (lineage.classifiedCategory() != null) {
            m.put("qualifyingTransactions", lineage.classifiedCategory());
        }
        if (DERIVABLE_FROM_AVAILABLE_DATA.equals(lineage.availability())
                && lineage.window() != null && lineage.window().contains("3")) {
            m.put("incompleteMonthNote",
                    "Current helper divides trailing 3-month totals by 3 (or amounts by 90). "
                            + "Complete-months-only is not defined — NEEDS_CONFIGURATION if business requires it.");
        }
        return m;
    }

    public static String friendlyAvailability(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case AVAILABLE_AUTOMATICALLY -> "Automatically calculated";
            case DERIVABLE_FROM_AVAILABLE_DATA -> "Automatically derived from available bank / bureau data";
            case MANUAL_INPUT_AVAILABLE -> "Manual input available";
            case MANUAL_REVIEW -> "Manual review";
            case UNAVAILABLE -> "Unavailable from current data";
            case NEEDS_CONFIGURATION -> "Needs configuration";
            default -> code;
        };
    }

    private static String friendlyName(String code) {
        String c = code.replace("banking.", "").replace("bureau.", "").replace('_', ' ');
        return c;
    }

    private static String inferSource(String code) {
        String l = code.toLowerCase(Locale.ROOT);
        if (l.startsWith("banking.") || l.contains("bank")) return "Bank statement";
        if (l.startsWith("bureau.")) return "Bureau report";
        if (l.startsWith("gst.")) return "GST";
        if (l.startsWith("itr.") || l.startsWith("financial.")) return "Financial statements / ITR";
        if (l.startsWith("application.")) return "Application";
        return "Application data";
    }
}
