package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthorableParameterProjection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds existing Policy DSL expressions from catalogue capability + business parameters.
 * Does not create a new rule engine — reuses PolicyDsl shapes already used by Studio.
 */
public final class CatalogueCapabilityExpressionBuilder {

    private CatalogueCapabilityExpressionBuilder() {}

    public record BuiltRule(
            Map<String, Object> expression,
            String onTrue,
            String onFalse,
            String onMissing,
            String systemRuleId,
            String businessSummary,
            List<String> dataUsed,
            String metricPath,
            Boolean needsDerivation,
            String derivationNote
    ) {
        public BuiltRule(
                Map<String, Object> expression,
                String onTrue,
                String onFalse,
                String onMissing,
                String systemRuleId,
                String businessSummary,
                List<String> dataUsed,
                String metricPath) {
            this(expression, onTrue, onFalse, onMissing, systemRuleId, businessSummary, dataUsed, metricPath,
                    false, null);
        }
    }

    public static BuiltRule build(BusinessCapability cap, Map<String, Object> parameters, String treatment) {
        Map<String, Object> params = parameters == null ? Map.of() : parameters;
        String failOutcome = mapTreatment(treatment, cap);
        String id = cap.businessCapabilityId();

        return switch (id) {
            case "BUREAU.MIN_SCORE", "ELIG.MIN_BUREAU_SCORE" -> {
                long score = longParam(params, "minimumScore", 650);
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric("bureau.score"), score),
                        failOutcome, "PASS",
                        "CATALOGUE_" + id.replace('.', '_'),
                        "Bureau score ≥ " + score,
                        List.of("bureau.score"),
                        "bureau.score");
            }
            case "BUREAU.LIVE_UNSECURED_MAX" -> {
                long max = longParam(params, "maximumCount", 6);
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric("bureau.live_unsecured_loan_count"), max),
                        failOutcome, "PASS",
                        "CATALOGUE_BUREAU_LIVE_UNSECURED_MAX",
                        "Live unsecured loans ≤ " + max,
                        List.of("bureau.live_unsecured_loan_count"),
                        "bureau.live_unsecured_loan_count");
            }
            case "BUREAU.ENQUIRIES_MAX" -> {
                long max = longParam(params, "maximumCount", 21);
                EnquiryMetric em = resolveEnquiryMetric(params);
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric(em.metricPath()), max),
                        failOutcome, "PASS",
                        "CATALOGUE_BUREAU_ENQUIRIES_MAX",
                        "Bureau enquiries (" + em.label() + ") ≤ " + max,
                        List.of(em.metricPath()),
                        em.metricPath(),
                        em.needsDerivation(),
                        em.derivationNote());
            }
            case "BUREAU.MAX_DPD" -> {
                long max = longParam(params, "maximumDays", 30);
                long window = longParam(params, "windowMonths", 6);
                String metric = window >= 12 ? "bureau.max_dpd_12m" : "bureau.max_dpd_6m";
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric(metric), max),
                        failOutcome, "PASS",
                        "CATALOGUE_BUREAU_MAX_DPD",
                        "Max DPD (" + window + "M) ≤ " + max,
                        List.of(metric),
                        metric);
            }
            case "BUREAU.CC_OVERDUE_MAX" -> {
                long max = longParam(params, "maximumAmount", 5000);
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric("bureau.cc_overdue_amount"), max),
                        failOutcome, "PASS",
                        "CATALOGUE_BUREAU_CC_OVERDUE_MAX",
                        "CC overdue < ₹" + formatInr(max),
                        List.of("bureau.cc_overdue_amount"),
                        "bureau.cc_overdue_amount");
            }
            case "BUREAU.NTC_ALLOWED" -> {
                boolean allow = boolParam(params, "allowNtc", true);
                yield built(
                        allow
                                ? PolicyDsl.eq(PolicyDsl.metric("bureau.status_ntc"), true)
                                : PolicyDsl.eq(PolicyDsl.metric("bureau.status_ntc"), false),
                        "PASS", failOutcome,
                        "CATALOGUE_BUREAU_NTC_ALLOWED",
                        allow ? "NTC allowed" : "NTC not allowed",
                        List.of("bureau.status_ntc"),
                        "bureau.status_ntc");
            }
            case "BANK.CHEQUE_BOUNCE_MAX" -> {
                long window = longParam(params, "windowMonths", 3);
                long max = longParam(params, "maximumCount", 0);
                String metric = window >= 12
                        ? "banking.cheque_return_count_12m"
                        : "banking.cheque_return_count_3m";
                Map<String, Object> expr = max == 0
                        ? PolicyDsl.gt(PolicyDsl.metric(metric), 0)
                        : PolicyDsl.gt(PolicyDsl.metric(metric), max);
                yield built(expr, failOutcome, "PASS",
                        "CATALOGUE_BANK_CHEQUE_BOUNCE_MAX",
                        "Cheque bounces (" + window + "M) ≤ " + max,
                        List.of(metric), metric);
            }
            case "BANK.TURNOVER_PCT_GST_MIN" -> {
                Number pct = decimalParam(params, "minimumPercentage", 75);
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric("banking.turnover_pct_gst"), pct),
                        failOutcome, "PASS",
                        "CATALOGUE_BANK_TURNOVER_PCT_GST_MIN",
                        "Banking/GST turnover ≥ " + pct + "%",
                        List.of("banking.turnover_pct_gst"),
                        "banking.turnover_pct_gst");
            }
            case "BANK.ABB_MIN" -> {
                long amt = longParam(params, "minimumAmount", 0);
                long window = longParam(params, "windowMonths", 3);
                String metric = "banking.avg_daily_balance_" + window + "m";
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric(metric), amt),
                        failOutcome, "PASS",
                        "CATALOGUE_BANK_ABB_MIN",
                        "ABB (" + window + "M) ≥ ₹" + formatInr(amt),
                        List.of(metric), metric);
            }
            case "BANK.CC_UTIL_MAX" -> {
                Number pct = decimalParam(params, "maximumPercentage", 95);
                yield built(
                        PolicyDsl.gte(PolicyDsl.metric("banking.cc_utilisation_pct"), pct),
                        failOutcome, "PASS",
                        "CATALOGUE_BANK_CC_UTIL_MAX",
                        "CC utilisation < " + pct + "%",
                        List.of("banking.cc_utilisation_pct"),
                        "banking.cc_utilisation_pct");
            }
            case "BANK.INWARD_RETURN_MAX" -> {
                Number pct = decimalParam(params, "maximumRatioPercent", 5);
                long window = longParam(params, "windowMonths", 3);
                String metric = "banking.inward_return.ratio_" + window + "m";
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric(metric), pct),
                        failOutcome, "PASS",
                        "CATALOGUE_BANK_INWARD_RETURN_MAX",
                        "Inward return ratio (" + window + "M) ≤ " + pct + "%",
                        List.of(metric), metric);
            }
            case "FIN.FOIR_MAX" -> {
                Number pct = decimalParam(params, "maximumPercentage", 50);
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric("obligation.ratio"), pct),
                        failOutcome, "PASS",
                        "CATALOGUE_FIN_FOIR_MAX",
                        "FOIR ≤ " + pct + "%",
                        List.of("obligation.ratio"),
                        "obligation.ratio");
            }
            case "FIN.DSCR_MIN" -> {
                Number ratio = decimalParam(params, "minimumRatio", new BigDecimal("1.25"));
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric("financial.dscr"), ratio),
                        failOutcome, "PASS",
                        "CATALOGUE_FIN_DSCR_MIN",
                        "DSCR ≥ " + ratio,
                        List.of("financial.dscr"),
                        "financial.dscr");
            }
            case "FIN.INTEREST_COVERAGE_MIN" -> {
                Number ratio = decimalParam(params, "minimumRatio", new BigDecimal("1.5"));
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric("financial.interest_coverage"), ratio),
                        failOutcome, "PASS",
                        "CATALOGUE_FIN_INTEREST_COVERAGE_MIN",
                        "Interest coverage ≥ " + ratio,
                        List.of("financial.interest_coverage"),
                        "financial.interest_coverage");
            }
            case "FIN.DEBT_EQUITY_MAX" -> {
                Number ratio = decimalParam(params, "maximumRatio", 2);
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric("financial.debt_equity"), ratio),
                        failOutcome, "PASS",
                        "CATALOGUE_FIN_DEBT_EQUITY_MAX",
                        "Debt/Equity ≤ " + ratio,
                        List.of("financial.debt_equity"),
                        "financial.debt_equity");
            }
            case "FIN.TOL_TNW_MAX" -> {
                Number ratio = decimalParam(params, "maximumRatio", 7);
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric("financial.tol_tnw"), ratio),
                        failOutcome, "PASS",
                        "CATALOGUE_FIN_TOL_TNW_MAX",
                        "TOL/TNW ≤ " + ratio,
                        List.of("financial.tol_tnw"),
                        "financial.tol_tnw");
            }
            case "FIN.ITR_INCOME_MIN" -> {
                long amt = longParam(params, "minimumAmount", 300_000);
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric("financial.itr_income"), amt),
                        failOutcome, "PASS",
                        "CATALOGUE_FIN_ITR_INCOME_MIN",
                        "ITR income ≥ ₹" + formatInr(amt),
                        List.of("financial.itr_income"),
                        "financial.itr_income");
            }
            case "FIN.PAT_POSITIVE" -> built(
                    PolicyDsl.lte(PolicyDsl.metric("financial.pat"), 0),
                    failOutcome, "PASS",
                    "CATALOGUE_FIN_PAT_POSITIVE",
                    "PAT must be positive",
                    List.of("financial.pat"),
                    "financial.pat");
            case "GST.TURNOVER_MIN" -> {
                long amt = longParam(params, "minimumAmount", 50_000_000);
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric("gst.annual_turnover"), amt),
                        failOutcome, "PASS",
                        "CATALOGUE_GST_TURNOVER_MIN",
                        "GST turnover ≥ ₹" + formatInr(amt),
                        List.of("gst.annual_turnover"),
                        "gst.annual_turnover");
            }
            case "ELIG.BUSINESS_VINTAGE_MIN" -> {
                long value = longParam(params, "minimumValue", 3);
                String unit = stringParam(params, "unit", "YEARS").toUpperCase(Locale.ROOT);
                // Store years in expression; MONTHS converted to fractional years for nearest supported unit.
                Number years = "MONTHS".equals(unit)
                        ? BigDecimal.valueOf(value).divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP)
                        : value;
                String summary = "MONTHS".equals(unit)
                        ? "Business vintage ≥ " + value + " months"
                        : "Business vintage ≥ " + value + " years";
                yield built(
                        PolicyDsl.lt(PolicyDsl.metric("business.vintage_years"), years),
                        failOutcome, "PASS",
                        "CATALOGUE_ELIG_BUSINESS_VINTAGE_MIN",
                        summary,
                        List.of("business.vintage_years"),
                        "business.vintage_years");
            }
            case "ELIG.MAX_REQUESTED_AMOUNT", "LIMIT.ABS_CAP" -> {
                long amt = longParam(params, "maximumAmount", 10_000_000);
                yield built(
                        PolicyDsl.gt(PolicyDsl.appField("requestedAmount"), amt),
                        failOutcome, "PASS",
                        "CATALOGUE_" + id.replace('.', '_'),
                        "Maximum ticket ≤ ₹" + formatInr(amt),
                        List.of("requestedAmount"),
                        "requestedAmount");
            }
            case "ELIG.REQUIRE_KYC_PASS" -> built(
                    PolicyDsl.eq(PolicyDsl.fact("kycPassEffective"), true),
                    "PASS", failOutcome,
                    "CATALOGUE_ELIG_REQUIRE_KYC_PASS",
                    "KYC must be successful",
                    List.of("kycPassEffective"),
                    "kycPassEffective");
            case "KYC.PAN_VERIFIED" -> built(
                    PolicyDsl.eq(PolicyDsl.fact("kyc.panVerified"), true),
                    "PASS", failOutcome,
                    "CATALOGUE_KYC_PAN_VERIFIED",
                    "PAN must be verified",
                    List.of("kyc.panVerified"),
                    "kyc.panVerified");
            case "KYC.GSTIN_VERIFIED" -> built(
                    PolicyDsl.eq(PolicyDsl.fact("kyc.gstinVerified"), true),
                    "PASS", failOutcome,
                    "CATALOGUE_KYC_GSTIN_VERIFIED",
                    "GSTIN must be verified",
                    List.of("kyc.gstinVerified"),
                    "kyc.gstinVerified");
            case "COLL.LTV_MAX" -> {
                Number pct = decimalParam(params, "maximumPercentage", 75);
                yield built(
                        PolicyDsl.gt(PolicyDsl.metric("collateral.ltv"), pct),
                        failOutcome, "PASS",
                        "CATALOGUE_COLL_LTV_MAX",
                        "LTV ≤ " + pct + "%",
                        List.of("collateral.ltv"),
                        "collateral.ltv");
            }
            case "DEC.DEFAULT_TREATMENT" -> built(
                    PolicyDsl.eq(PolicyDsl.fact("policy.defaultGate"), true),
                    "PASS", failOutcome,
                    "CATALOGUE_DEC_DEFAULT_TREATMENT",
                    "Default decision treatment: " + failOutcome,
                    List.of("policy.defaultGate"),
                    "policy.defaultGate");
            default -> {
                if (PolicyAuthorableParameterProjection.isAuthorable(id)) {
                    yield gacatAuthorableComparison(cap, params, failOutcome);
                }
                // Legacy unknown template — placeholder only; not a silent executable mapping.
                Map<String, Object> expr = new LinkedHashMap<>();
                expr.put("op", "CATALOGUE_CAPABILITY");
                expr.put("businessCapabilityId", id);
                expr.put("parameters", new LinkedHashMap<>(params));
                yield built(expr, failOutcome, "PASS",
                        "CATALOGUE_" + id.replace('.', '_'),
                        cap.businessName(),
                        List.of(cap.factOrMeasure() == null ? id : cap.factOrMeasure()),
                        cap.factOrMeasure());
            }
        };
    }

    /** Validate business parameters; returns business-friendly errors (empty if ok). */
    public static List<String> validate(BusinessCapability cap, Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> params = parameters == null ? Map.of() : parameters;
        for (ParameterDefinition def : cap.parameterDefinitions()) {
            Object raw = params.get(def.name());
            if (raw == null || String.valueOf(raw).isBlank()) {
                if (def.defaultValue() == null && cap.parameterisable()) {
                    errors.add(def.label() + " is required");
                }
                continue;
            }
            String type = def.type() == null ? "" : def.type().toUpperCase(Locale.ROOT);
            try {
                switch (type) {
                    case "PERCENT" -> {
                        double v = Double.parseDouble(String.valueOf(raw));
                        if (v < 0 || v > 100) {
                            errors.add(def.label() + " must be between 0 and 100");
                        }
                    }
                    case "INTEGER", "NUMBER", "SCORE" -> {
                        double v = Double.parseDouble(String.valueOf(raw));
                        if ("SCORE".equals(type) && (v < 300 || v > 900)) {
                            errors.add(def.label() + " should be a reasonable bureau score (300–900)");
                        }
                        if (("windowMonths".equals(def.name()) || def.name().toLowerCase(Locale.ROOT).contains("window"))
                                && v <= 0) {
                            errors.add(def.label() + " must be greater than 0");
                        }
                        if (v < 0 && !"SCORE".equals(type)) {
                            errors.add(def.label() + " cannot be negative");
                        }
                    }
                    case "DECIMAL" -> {
                        double v = Double.parseDouble(String.valueOf(raw));
                        if (v < 0) {
                            errors.add(def.label() + " cannot be negative");
                        }
                    }
                    case "MONEY_INR" -> {
                        double v = Double.parseDouble(String.valueOf(raw).replace(",", "").replace("₹", "").trim());
                        if (v < 0) {
                            errors.add(def.label() + " must be a positive amount");
                        }
                    }
                    case "ENUM" -> {
                        /* free-form enum ok */
                    }
                    default -> {
                        /* accept */
                    }
                }
            } catch (NumberFormatException e) {
                errors.add(def.label() + " must be a valid number");
            }
        }
        return errors;
    }

    public static String mapTreatment(String treatment, BusinessCapability cap) {
        final String requested = treatment == null || treatment.isBlank()
                ? "REJECT" : treatment.trim().toUpperCase(Locale.ROOT);
        final String t;
        if (cap != null && cap.supportedTreatments() != null && !cap.supportedTreatments().isEmpty()
                && cap.supportedTreatments().stream().noneMatch(s -> s.equalsIgnoreCase(requested))) {
            t = cap.supportedTreatments().get(0);
        } else {
            t = requested;
        }
        return switch (t) {
            case "MANUAL_REVIEW", "REFER" -> "REFER";
            case "WARNING" -> "WARNING";
            case "SCORE_IMPACT" -> "SCORE_IMPACT";
            case "LIMIT_ADJUSTMENT", "LIMIT" -> "LIMIT_ADJUSTMENT";
            case "PRICING", "PRICE_ADJUSTMENT" -> "PRICE_ADJUSTMENT";
            default -> "FAIL";
        };
    }

    public static String treatmentLabel(String onTrueOrTreatment) {
        if (onTrueOrTreatment == null) return "Reject";
        return switch (onTrueOrTreatment.toUpperCase(Locale.ROOT)) {
            case "REFER", "MANUAL_REVIEW" -> "Manual Review";
            case "WARNING" -> "Warning";
            case "SCORE_IMPACT" -> "Score Impact";
            case "LIMIT_ADJUSTMENT", "LIMIT" -> "Limit adjustment";
            case "PRICE_ADJUSTMENT", "PRICING" -> "Pricing";
            default -> "Reject";
        };
    }

    /**
     * GACAT Add Rule: the authored predicate is the eligibility condition.
     * {@code whenMatched=PASS} (default) → condition true means borrower passes.
     * {@code whenMatched=FAIL} → condition true is an adverse event.
     */
    private static BuiltRule gacatAuthorableComparison(
            BusinessCapability cap, Map<String, Object> params, String failOutcome) {
        String metric = cap.factOrMeasure() == null ? cap.businessCapabilityId() : cap.factOrMeasure();
        String operator = stringParam(params, "operator", "LTE").toUpperCase(Locale.ROOT);
        Object threshold = params.get("threshold");
        if (threshold == null || String.valueOf(threshold).isBlank()) {
            threshold = 0;
        }
        if (threshold instanceof String s) {
            String t = s.trim();
            if ("true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t)) {
                threshold = Boolean.parseBoolean(t);
            } else {
                try {
                    if (t.contains(".")) {
                        threshold = new BigDecimal(t);
                    } else {
                        threshold = Long.parseLong(t);
                    }
                } catch (NumberFormatException ignored) {
                    // keep string
                }
            }
        }
        Map<String, Object> expression = switch (operator) {
            case "GT" -> PolicyDsl.gt(PolicyDsl.metric(metric), threshold);
            case "GTE" -> PolicyDsl.gte(PolicyDsl.metric(metric), threshold);
            case "LT" -> PolicyDsl.lt(PolicyDsl.metric(metric), threshold);
            case "EQ" -> PolicyDsl.eq(PolicyDsl.metric(metric), threshold);
            default -> PolicyDsl.lte(PolicyDsl.metric(metric), threshold);
        };
        String whenMatched = stringParam(params, "whenMatched", "PASS").toUpperCase(Locale.ROOT);
        boolean adverse = "FAIL".equals(whenMatched) || "ADVERSE".equals(whenMatched);
        String onTrue = adverse ? failOutcome : "PASS";
        String onFalse = adverse ? "PASS" : failOutcome;
        String summary = cap.businessName() + " " + operator + " " + threshold
                + (adverse ? " (adverse when matched)" : " (eligibility when matched)");
        return built(expression, onTrue, onFalse,
                "GACAT_" + metric.replace('.', '_').toUpperCase(Locale.ROOT),
                summary, List.of(metric), metric);
    }

    private static BuiltRule built(
            Map<String, Object> expression,
            String onTrue,
            String onFalse,
            String systemRuleId,
            String summary,
            List<String> dataUsed,
            String metricPath) {
        return new BuiltRule(expression, onTrue, onFalse, "DATA_INSUFFICIENT",
                systemRuleId, summary, dataUsed, metricPath, false, null);
    }

    private static BuiltRule built(
            Map<String, Object> expression,
            String onTrue,
            String onFalse,
            String systemRuleId,
            String summary,
            List<String> dataUsed,
            String metricPath,
            boolean needsDerivation,
            String derivationNote) {
        return new BuiltRule(expression, onTrue, onFalse, "DATA_INSUFFICIENT",
                systemRuleId, summary, dataUsed, metricPath, needsDerivation, derivationNote);
    }

    private record EnquiryMetric(String metricPath, String label, boolean needsDerivation, String derivationNote) {}

    /**
     * Maps windowKind / months / days to canonical enquiry metrics.
     * Never collapses current-month into 3-month or 90-day into calendar months.
     */
    private static EnquiryMetric resolveEnquiryMetric(Map<String, Object> params) {
        String kind = stringParam(params, "windowKind", null);
        Long days = params.get("windowDays") instanceof Number n ? n.longValue() : null;
        Long months = params.get("windowMonths") instanceof Number n ? n.longValue() : null;
        if (kind == null || kind.isBlank()) {
            if (days != null && days == 90) {
                kind = EnquiryWindowSpec.LAST_90_DAYS;
            } else if (days != null && days == 30) {
                kind = EnquiryWindowSpec.LAST_30_DAYS;
            } else if (months != null && months == 0) {
                kind = EnquiryWindowSpec.CURRENT_MONTH;
            } else if (months != null && months == 3) {
                kind = EnquiryWindowSpec.LAST_3_MONTHS;
            } else if (months != null && months == 6) {
                kind = EnquiryWindowSpec.LAST_6_MONTHS;
            } else if (months != null && months == 12) {
                kind = EnquiryWindowSpec.LAST_12_MONTHS;
            }
        }
        if (kind == null) {
            return new EnquiryMetric(
                    "bureau.inquiries.current_month",
                    "window unspecified",
                    true,
                    "NEEDS_DERIVATION: enquiry window not specified — do not assume 3 months");
        }
        return switch (kind) {
            case EnquiryWindowSpec.CURRENT_MONTH -> new EnquiryMetric(
                    "bureau.inquiries.current_month", "current month", false, null);
            case EnquiryWindowSpec.LAST_90_DAYS -> new EnquiryMetric(
                    "bureau.recent_inquiries_90d", "last 90 days", false, null);
            case EnquiryWindowSpec.LAST_3_MONTHS -> new EnquiryMetric(
                    "bureau.inquiries.last_3m", "last 3 months", false, null);
            case EnquiryWindowSpec.LAST_30_DAYS -> new EnquiryMetric(
                    "bureau.inquiries.last_30d", "last 30 days", true,
                    "NEEDS_DERIVATION: last-30-day enquiry count not production-backed");
            case EnquiryWindowSpec.LAST_6_MONTHS -> new EnquiryMetric(
                    "bureau.inquiries.last_6m", "last 6 months", true,
                    "NEEDS_DERIVATION: last-6-month enquiry count not production-backed");
            case EnquiryWindowSpec.LAST_12_MONTHS -> new EnquiryMetric(
                    "bureau.inquiries.last_12m", "last 12 months", true,
                    "NEEDS_DERIVATION: last-12-month enquiry count not production-backed");
            default -> new EnquiryMetric(
                    "bureau.inquiries.current_month", kind, true,
                    "NEEDS_DERIVATION: unsupported enquiry window " + kind);
        };
    }

    private static long longParam(Map<String, Object> params, String key, long def) {
        Object v = params.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        String s = String.valueOf(v).replace(",", "").replace("₹", "").trim();
        if (s.isBlank()) return def;
        return new BigDecimal(s).longValue();
    }

    private static Number decimalParam(Map<String, Object> params, String key, Number def) {
        Object v = params.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n;
        String s = String.valueOf(v).replace("%", "").trim();
        if (s.isBlank()) return def;
        return new BigDecimal(s);
    }

    private static String stringParam(Map<String, Object> params, String key, String def) {
        Object v = params.get(key);
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? def : s;
    }

    private static boolean boolParam(Map<String, Object> params, String key, boolean def) {
        Object v = params.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "yes".equals(s) || "1".equals(s)) return true;
        if ("false".equals(s) || "no".equals(s) || "0".equals(s)) return false;
        return def;
    }

    private static String formatInr(long amount) {
        return String.format(Locale.ENGLISH, "%,d", amount);
    }
}
