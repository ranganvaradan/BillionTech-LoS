package com.los.core.creditintelligence.validation.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Measures canonical coverage percentages and critical-rule coverage separately.
 */
@Component
public class CanonicalCoverageCalculator {

    public static final List<String> PRODUCTION_INPUTS = List.of(
            "LIVE_UNSECURED_LOAN_COUNT",
            "ANNUAL_GST_TURNOVER",
            "ANNUAL_BANKING_TURNOVER",
            "AVERAGE_BANK_BALANCE",
            "EMI_OBLIGATION",
            "ITR_INCOME",
            "PAT",
            "TOL",
            "TNW",
            "OBLIGATION_RATIO",
            "DTI_RATIO",
            "MONTHLY_INCOME",
            "BUREAU_ENQUIRIES_3M",
            "NTC_FLAG",
            "CHEQUE_BOUNCES_12M"
    );

    public static final Set<String> CRITICAL_INPUTS = Set.of(
            "LIVE_UNSECURED_LOAN_COUNT",
            "ANNUAL_GST_TURNOVER",
            "AVERAGE_BANK_BALANCE",
            "EMI_OBLIGATION",
            "ITR_INCOME",
            "OBLIGATION_RATIO",
            "ANNUAL_BANKING_TURNOVER"
    );

    public Map<String, Object> calculate(
            Map<String, BigDecimal> metricStubs,
            Map<String, String> sources,
            Map<String, Object> reconOutcomes) {
        int total = PRODUCTION_INPUTS.size();
        int mapped = 0;
        int verified = 0;
        int derived = 0;
        int reconciled = 0;
        int manual = 0;
        int defaulted = 0;
        int di = 0;
        int legacyOnly = 0;
        int criticalMapped = 0;
        int criticalTotal = CRITICAL_INPUTS.size();
        List<Map<String, Object>> detail = new ArrayList<>();

        boolean hasBureau = sources != null && sources.containsKey("bureau");
        boolean hasGst = sources != null && sources.containsKey("gst");
        boolean hasBank = sources != null && sources.containsKey("bank");
        boolean hasItr = sources != null && sources.containsKey("itr");

        for (String key : PRODUCTION_INPUTS) {
            String status;
            String classification;
            switch (key) {
                case "LIVE_UNSECURED_LOAN_COUNT" -> {
                    if (hasBureau && metricStubs != null && metricStubs.containsKey("bureau.live_unsecured_count")) {
                        status = "VERIFIED";
                        classification = "VERIFIED";
                        verified++;
                        mapped++;
                    } else {
                        status = "DATA_INSUFFICIENT";
                        classification = "DATA_INSUFFICIENT";
                        di++;
                    }
                }
                case "ANNUAL_GST_TURNOVER" -> {
                    if (hasGst && metricStubs != null && metricStubs.containsKey("gst.turnover.trailing_12m")) {
                        status = "VERIFIED";
                        classification = "VERIFIED";
                        verified++;
                        mapped++;
                    } else {
                        status = "DATA_INSUFFICIENT";
                        classification = "DATA_INSUFFICIENT";
                        di++;
                    }
                }
                case "ANNUAL_BANKING_TURNOVER", "AVERAGE_BANK_BALANCE" -> {
                    if (hasBank) {
                        status = "DERIVED";
                        classification = "DERIVED";
                        derived++;
                        mapped++;
                    } else {
                        status = "DATA_INSUFFICIENT";
                        classification = "DATA_INSUFFICIENT";
                        di++;
                    }
                }
                case "EMI_OBLIGATION" -> {
                    if (hasBureau || hasBank) {
                        status = "DERIVED";
                        classification = "DERIVED";
                        derived++;
                        mapped++;
                        if (reconOutcomes != null && reconOutcomes.containsKey("XSRC_BUREAU_BANK_OBLIGATION")) {
                            reconciled++;
                            classification = "RECONCILED";
                        }
                    } else {
                        status = "DATA_INSUFFICIENT";
                        classification = "DATA_INSUFFICIENT";
                        di++;
                    }
                }
                case "ITR_INCOME" -> {
                    if (hasItr) {
                        status = "VERIFIED";
                        classification = "VERIFIED";
                        verified++;
                        mapped++;
                    } else {
                        status = "DATA_INSUFFICIENT";
                        classification = "DATA_INSUFFICIENT";
                        di++;
                    }
                }
                case "OBLIGATION_RATIO", "DTI_RATIO", "MONTHLY_INCOME" -> {
                    if ((hasItr || hasBank) && (hasBureau || hasBank)) {
                        status = "DERIVED";
                        classification = "DERIVED";
                        derived++;
                        mapped++;
                    } else {
                        status = "DATA_INSUFFICIENT";
                        classification = "DATA_INSUFFICIENT";
                        di++;
                    }
                }
                case "PAT", "TOL", "TNW" -> {
                    if (hasItr) {
                        status = "MAPPED_PARTIAL";
                        classification = "DERIVED";
                        derived++;
                        mapped++;
                    } else {
                        status = "PROVIDER_COUPLED_LEGACY_ONLY";
                        classification = "LEGACY_ONLY";
                        legacyOnly++;
                    }
                }
                default -> {
                    status = "PROVIDER_COUPLED_LEGACY_ONLY";
                    classification = "LEGACY_ONLY";
                    legacyOnly++;
                }
            }
            if (CRITICAL_INPUTS.contains(key) && !"DATA_INSUFFICIENT".equals(status)
                    && !"PROVIDER_COUPLED_LEGACY_ONLY".equals(status)) {
                criticalMapped++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("input", key);
            row.put("status", status);
            row.put("classification", classification);
            row.put("critical", CRITICAL_INPUTS.contains(key));
            detail.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productionInputsTotal", total);
        out.put("canonicalMappingAvailable", mapped);
        out.put("canonicalVerified", verified);
        out.put("canonicalDerived", derived);
        out.put("canonicalReconciled", reconciled);
        out.put("canonicalManual", manual);
        out.put("canonicalDefaulted", defaulted);
        out.put("canonicalDataInsufficient", di);
        out.put("providerCoupledLegacyOnly", legacyOnly);
        out.put("overallCoveragePct", pct(mapped, total));
        out.put("criticalCoveragePct", pct(criticalMapped, criticalTotal));
        out.put("criticalMapped", criticalMapped);
        out.put("criticalTotal", criticalTotal);
        out.put("detail", detail);
        return out;
    }

    private static BigDecimal pct(int num, int den) {
        if (den == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(num * 100.0 / den).setScale(2, RoundingMode.HALF_UP);
    }
}
