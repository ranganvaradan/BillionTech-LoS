package com.los.core.creditintelligence.validation.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static inventory of CreditControl gap/demo defaults and scorecard keys still authoritative in production.
 */
@Component
public class LegacyDefaultInventory {

    public record LegacyDefaultEntry(
            String legacyKey,
            String origin,
            String defaultValue,
            String canonicalPath,
            boolean critical,
            List<String> rulesImpacted
    ) {
    }

    public List<LegacyDefaultEntry> inventory() {
        List<LegacyDefaultEntry> list = new ArrayList<>();
        list.add(e("MONTHLY_INCOME", "GAP_DEFAULT", "85000", "metric:itr.income / bank.income", true,
                List.of("FOIR", "DTI")));
        list.add(e("EMI_OBLIGATION", "GAP_DEFAULT", "15000", "metric:bureau.emi / bank.emi", true,
                List.of("FOIR", "OBLIGATION_RATIO")));
        list.add(e("AVERAGE_BANK_BALANCE", "GAP_DEFAULT", "120000", "metric:bank.abb.average", true,
                List.of("ABB_RULE")));
        list.add(e("OBLIGATION_RATIO", "GAP_DEFAULT_FOIR", "25", "metric:obligation_ratio", true,
                List.of("FOIR")));
        list.add(e("DTI_RATIO", "GAP_DEFAULT", "18", "metric:dti_ratio", true, List.of("DTI")));
        list.add(e("LIVE_UNSECURED_LOAN_COUNT", "GAP_DEFAULT", "2", "metric:bureau.live_unsecured_count", true,
                List.of("LIVE_UNSECURED_HARD_RULE")));
        list.add(e("ANNUAL_GST_TURNOVER", "SCF_GAP_DEFAULT", "52000000", "metric:gst.turnover.trailing_12m", true,
                List.of("SCF_TURNOVER")));
        list.add(e("ANNUAL_BANKING_TURNOVER", "SCF_GAP_DEFAULT", "41000000", "metric:bank.turnover.trailing_12m", true,
                List.of("SCF_TURNOVER")));
        list.add(e("ITR_INCOME", "SCF_GAP_DEFAULT", "450000", "metric:itr.income.total", true,
                List.of("INCOME_RULE")));
        list.add(e("PAT", "SCF_GAP_DEFAULT", "500000", "metric:itr.pat", false, List.of("SCF_FINANCIALS")));
        list.add(e("TOL", "GAP_DEFAULT", "3500000", "metric:financials.tol", false, List.of("LEVERAGE")));
        list.add(e("TNW", "GAP_DEFAULT", "5000000", "metric:financials.tnw", false, List.of("LEVERAGE")));
        list.add(e("BUREAU_ENQUIRIES_3M", "GAP_DEFAULT", "5", "metric:bureau.enquiries_3m", false,
                List.of("ENQUIRY_RULE")));
        list.add(e("PROVIDER_GAP_DEFAULT_ACTIVE", "FLAG", "1", "n/a — production flag", true,
                List.of("ALL_GAP_DEPENDENT")));
        list.add(e("DEMO_FALLBACK_ACTIVE", "DEMO_FALLBACK", "1", "n/a — demo path", true,
                List.of("BUREAU_DEMO", "KYC_DEMO", "INCOME_DEMO")));
        list.add(e("avgDailyBalance3m", "GAP_DEFAULT_BANK", "120000", "metric:bank.abb", false,
                List.of("BANK_ANALYTICS")));
        list.add(e("INTEREST_COVERAGE", "SCF_GAP_DEFAULT", "1.6", "metric:financials.interest_coverage", false,
                List.of("SCF_FINANCIALS")));
        list.add(e("DEBT_TO_EQUITY", "SCF_GAP_DEFAULT", "1.5", "metric:financials.dte", false,
                List.of("SCF_FINANCIALS")));
        return list;
    }

    /**
     * Per-case exposure table comparing legacy stub values vs canonical metric stubs / DI status.
     */
    public Map<String, Object> exposeForCase(
            Map<String, Object> legacyScorecard,
            Map<String, BigDecimal> canonicalMetrics,
            boolean bankPresent,
            boolean bureauPresent) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int defaultDependent = 0;
        for (LegacyDefaultEntry entry : inventory()) {
            Object legacyVal = legacyScorecard != null ? legacyScorecard.get(entry.legacyKey()) : null;
            String canonicalStatus;
            Object canonicalValue = null;
            String difference;
            if ("LIVE_UNSECURED_LOAN_COUNT".equals(entry.legacyKey())) {
                if (!bureauPresent) {
                    canonicalStatus = "DATA_INSUFFICIENT";
                    difference = "LEGACY_DEFAULT_DEPENDENT";
                    defaultDependent++;
                } else {
                    canonicalValue = canonicalMetrics.get("bureau.live_unsecured_count");
                    canonicalStatus = canonicalValue != null ? "VERIFIED" : "DATA_INSUFFICIENT";
                    difference = sameish(legacyVal, canonicalValue) ? "MATCH" : "VALUE_DIFF";
                }
            } else if (entry.legacyKey().contains("BANK") || entry.legacyKey().startsWith("avg")
                    || "AVERAGE_BANK_BALANCE".equals(entry.legacyKey())
                    || "ANNUAL_BANKING_TURNOVER".equals(entry.legacyKey())
                    || "EMI_OBLIGATION".equals(entry.legacyKey())) {
                if (!bankPresent && !"EMI_OBLIGATION".equals(entry.legacyKey())) {
                    canonicalStatus = "DATA_INSUFFICIENT";
                    difference = "LEGACY_DEFAULT_DEPENDENT";
                    defaultDependent++;
                } else if ("EMI_OBLIGATION".equals(entry.legacyKey()) && !bureauPresent && !bankPresent) {
                    canonicalStatus = "DATA_INSUFFICIENT";
                    difference = "LEGACY_DEFAULT_DEPENDENT";
                    defaultDependent++;
                } else {
                    canonicalValue = pickMetric(canonicalMetrics, entry.legacyKey());
                    canonicalStatus = canonicalValue != null ? "DERIVED" : "DATA_INSUFFICIENT";
                    difference = canonicalValue == null ? "LEGACY_DEFAULT_DEPENDENT"
                            : (sameish(legacyVal, canonicalValue) ? "MATCH" : "VALUE_DIFF");
                    if ("LEGACY_DEFAULT_DEPENDENT".equals(difference)) {
                        defaultDependent++;
                    }
                }
            } else if ("ANNUAL_GST_TURNOVER".equals(entry.legacyKey())) {
                canonicalValue = canonicalMetrics.get("gst.turnover.trailing_12m");
                if (canonicalValue == null) {
                    canonicalStatus = "DATA_INSUFFICIENT";
                    difference = "LEGACY_DEFAULT_DEPENDENT";
                    defaultDependent++;
                } else {
                    canonicalStatus = "VERIFIED";
                    difference = sameish(legacyVal, canonicalValue) ? "MATCH" : "VALUE_DIFF";
                }
            } else if (entry.origin().contains("GAP") || entry.origin().contains("DEMO")
                    || entry.origin().contains("FLAG")) {
                canonicalValue = pickMetric(canonicalMetrics, entry.legacyKey());
                canonicalStatus = canonicalValue != null ? "DERIVED" : "DATA_INSUFFICIENT";
                if (legacyVal != null && canonicalValue == null) {
                    difference = "LEGACY_DEFAULT_DEPENDENT";
                    defaultDependent++;
                } else {
                    difference = sameish(legacyVal, canonicalValue) ? "MATCH" : "VALUE_DIFF";
                }
            } else {
                canonicalStatus = "MAPPED";
                difference = "OTHER";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("legacyKey", entry.legacyKey());
            row.put("legacyValue", legacyVal != null ? legacyVal : entry.defaultValue());
            row.put("origin", entry.origin());
            row.put("canonicalPath", entry.canonicalPath());
            row.put("canonicalValue", canonicalValue);
            row.put("canonicalStatus", canonicalStatus);
            row.put("difference", difference);
            row.put("rulesImpacted", entry.rulesImpacted());
            row.put("critical", entry.critical());
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("defaultDependentCount", defaultDependent);
        out.put("inventorySize", inventory().size());
        return out;
    }

    private static Object pickMetric(Map<String, BigDecimal> metrics, String legacyKey) {
        if (metrics == null) {
            return null;
        }
        return switch (legacyKey) {
            case "AVERAGE_BANK_BALANCE", "avgDailyBalance3m" -> metrics.get("bank.abb.average");
            case "ANNUAL_BANKING_TURNOVER" -> metrics.get("bank.turnover.trailing_12m");
            case "ANNUAL_GST_TURNOVER" -> metrics.get("gst.turnover.trailing_12m");
            case "EMI_OBLIGATION" -> first(metrics.get("bureau.emi.monthly"), metrics.get("bank.emi.monthly"));
            case "ITR_INCOME" -> metrics.get("itr.income.total");
            case "LIVE_UNSECURED_LOAN_COUNT" -> metrics.get("bureau.live_unsecured_count");
            case "OBLIGATION_RATIO" -> metrics.get("obligation.ratio");
            default -> null;
        };
    }

    private static Object first(Object a, Object b) {
        return a != null ? a : b;
    }

    private static boolean sameish(Object a, Object b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        try {
            return new BigDecimal(String.valueOf(a)).compareTo(new BigDecimal(String.valueOf(b))) == 0;
        } catch (Exception e) {
            return String.valueOf(a).equals(String.valueOf(b));
        }
    }

    private static LegacyDefaultEntry e(
            String key, String origin, String value, String path, boolean critical, List<String> rules) {
        return new LegacyDefaultEntry(key, origin, value, path, critical, rules);
    }
}
