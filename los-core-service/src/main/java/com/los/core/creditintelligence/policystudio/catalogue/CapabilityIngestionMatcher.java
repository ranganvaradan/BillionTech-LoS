package com.los.core.creditintelligence.policystudio.catalogue;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * POLICY-UX-2D — match clause text to catalogue capabilities and extract explicit parameters.
 * Golden templates are hints only; explicit policy values win.
 */
@Component
public class CapabilityIngestionMatcher {

    public record MatchResult(
            IngestionMatchClassification classification,
            String businessCapabilityId,
            Map<String, Object> extractedParameters,
            Map<String, Object> catalogueDefaultParameters,
            boolean needsInput,
            boolean parameterDiffers,
            String confidence, // HIGH | MEDIUM | LOW
            String failureTreatment,
            String rationale,
            String businessSummary,
            boolean activationIncluded,
            String manualInputLabel,
            String manualInputType
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("classification", classification.name());
            m.put("businessCapabilityId", businessCapabilityId);
            m.put("extractedParameters", extractedParameters);
            m.put("catalogueDefaultParameters", catalogueDefaultParameters);
            m.put("NEEDS_INPUT", needsInput);
            m.put("PARAMETER_DIFFERS", parameterDiffers);
            m.put("matchConfidence", confidence);
            m.put("failureTreatment", failureTreatment);
            m.put("rationale", rationale);
            m.put("businessSummary", businessSummary);
            m.put("activationIncluded", activationIncluded);
            if (manualInputLabel != null) m.put("manualInputLabel", manualInputLabel);
            if (manualInputType != null) m.put("manualInputType", manualInputType);
            return m;
        }
    }

    private final CreditCapabilityCatalogueService catalogue;

    public CapabilityIngestionMatcher(CreditCapabilityCatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    public CapabilityIngestionMatcher() {
        this(new CreditCapabilityCatalogueService());
    }

    public MatchResult match(String sourceText) {
        String text = sourceText == null ? "" : sourceText.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        Optional<MatchResult> nonUw = classifyNonUnderwriting(text, lower);
        if (nonUw.isPresent()) {
            return nonUw.get();
        }

        Optional<MatchResult> cap = matchCapability(text, lower);
        if (cap.isPresent()) {
            return cap.get();
        }

        if (looksLikeManualReview(lower)) {
            return nonExecutable(IngestionMatchClassification.MANUAL_REVIEW, text,
                    "Human judgement required", "LOW", false);
        }
        if (looksLikeManualInput(lower)) {
            return manualInput(text, inferManualLabel(text), "NUMBER");
        }

        return nonExecutable(IngestionMatchClassification.AMBIGUOUS, text,
                "Could not confidently match an existing capability", "LOW", false);
    }

    private Optional<MatchResult> matchCapability(String text, String lower) {
        // Bureau score / CIBIL
        if (containsAny(lower, "bureau score", "cibil", "credit bureau score", "credit score")
                || (lower.contains("bureau") && lower.contains("score"))
                || (lower.contains("cibil") && (lower.contains("minimum") || lower.contains("at least") || lower.contains("gte")))) {
            Long score = extractNumber(text, "(?i)(?:minimum|at\\s+least|>=|≥|min(?:imum)?\\s*(?:of)?)\\s*(\\d{3})",
                    "(?i)(?:score|cibil).*?(?:of\\s+)?(\\d{3})",
                    "(?i)(\\d{3})\\s*(?:or\\s+higher|and\\s+above)");
            return Optional.of(capabilityMatch("BUREAU.MIN_SCORE",
                    params("minimumScore", score),
                    score != null ? "Bureau score ≥ " + score : "Bureau score (value missing)",
                    score == null));
        }

        // Live unsecured
        if (containsAny(lower, "live unsecured", "unsecured loan", "unsecured facilities")) {
            Long max = extractNumber(text, "(?i)(?:not\\s+exceed|maximum|max|<=|≤|upto|up\\s+to)\\s*(\\d+)",
                    "(?i)(\\d+)\\s*(?:or\\s+fewer|live\\s+unsecured)");
            return Optional.of(capabilityMatch("BUREAU.LIVE_UNSECURED_MAX",
                    params("maximumCount", max),
                    max != null ? "Live unsecured loans ≤ " + max : "Live unsecured loans (value missing)",
                    max == null));
        }

        // Enquiries
        if (containsAny(lower, "enquir", "inquir")) {
            Long max = extractNumber(text, "(?i)(?:not\\s+exceed|maximum|max|<=|≤|upto|up\\s+to)\\s*(\\d+)",
                    "(?i)(\\d+)\\s*(?:enquir|inquir)");
            Long window = extractWindowMonths(text, lower);
            Map<String, Object> p = new LinkedHashMap<>();
            if (max != null) p.put("maximumCount", max);
            p.put("windowMonths", window != null ? window : 3L);
            return Optional.of(capabilityMatch("BUREAU.ENQUIRIES_MAX", p,
                    "Bureau enquiries (" + p.get("windowMonths") + "M) ≤ " + (max != null ? max : "?"),
                    max == null));
        }

        // Cheque bounce / return
        if (containsAny(lower, "cheque bounce", "check bounce", "cheque return", "bounce during",
                "cheque/payment return", "no cheque bounce", "cheque bounces")) {
            Long window = extractWindowMonths(text, lower);
            Long max;
            if (containsAny(lower, "no cheque", "nil", "zero", "should not have", "must not have", "without any")) {
                max = 0L;
            } else {
                max = extractNumber(text, "(?i)(?:not\\s+exceed|maximum|max|<=|≤|upto|up\\s+to)\\s*(\\d+)",
                        "(?i)(\\d+)\\s*(?:bounce|return)");
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("windowMonths", window != null ? window : 3L);
            if (max != null) p.put("maximumCount", max);
            boolean needs = max == null;
            return Optional.of(capabilityMatch("BANK.CHEQUE_BOUNCE_MAX", p,
                    "Cheque bounces (" + p.get("windowMonths") + "M) ≤ " + (max != null ? max : "?"),
                    needs));
        }

        // Bank/GST turnover %
        if ((containsAny(lower, "banking turnover", "bank turnover", "banking/gst", "bank/gst")
                && containsAny(lower, "gst", "%", "percent", "percentage"))
                || (lower.contains("turnover") && lower.contains("gst") && containsAny(lower, "%", "percent"))) {
            Long pct = extractNumber(text, "(?i)(?:at\\s+least|minimum|min(?:imum)?\\s*(?:of)?|>=|≥)\\s*(\\d{1,3})\\s*%?",
                    "(?i)(\\d{1,3})\\s*%");
            return Optional.of(capabilityMatch("BANK.TURNOVER_PCT_GST_MIN",
                    params("minimumPercentage", pct),
                    pct != null ? "Banking/GST turnover ≥ " + pct + "%" : "Banking/GST turnover (value missing)",
                    pct == null));
        }

        // CC utilisation
        if (containsAny(lower, "cc utilisation", "cc utilization", "credit card util", "card utilisation", "card utilization")) {
            Long pct = extractNumber(text, "(?i)(?:less\\s+than|below|under|<|not\\s+exceed|maximum|max)\\s*(\\d{1,3})\\s*%?",
                    "(?i)(\\d{1,3})\\s*%");
            return Optional.of(capabilityMatch("BANK.CC_UTIL_MAX",
                    params("maximumPercentage", pct),
                    pct != null ? "CC utilisation < " + pct + "%" : "CC utilisation (value missing)",
                    pct == null));
        }

        // FOIR / obligation
        if (containsAny(lower, "foir", "obligation ratio", "fixed obligation")) {
            Long pct = extractNumber(text, "(?i)(?:not\\s+exceed|maximum|max|<=|≤|upto|up\\s+to)\\s*(\\d{1,3})\\s*%?",
                    "(?i)(\\d{1,3})\\s*%");
            return Optional.of(capabilityMatch("FIN.FOIR_MAX",
                    params("maximumPercentage", pct),
                    pct != null ? "FOIR ≤ " + pct + "%" : "FOIR (value missing)",
                    pct == null));
        }

        // DSCR
        if (containsAny(lower, "dscr", "debt service coverage")) {
            BigDecimal ratio = extractDecimal(text,
                    "(?i)(?:minimum|at\\s+least|min(?:imum)?\\s*(?:of)?|>=|≥|>)\\s*(\\d+(?:\\.\\d+)?)",
                    "(?i)(\\d+(?:\\.\\d+)?)");
            Map<String, Object> p = new LinkedHashMap<>();
            if (ratio != null) p.put("minimumRatio", ratio);
            return Optional.of(capabilityMatch("FIN.DSCR_MIN", p,
                    ratio != null ? "DSCR ≥ " + ratio : "DSCR (value missing)",
                    ratio == null));
        }

        // Interest coverage
        if (containsAny(lower, "interest coverage", "interest cover")) {
            BigDecimal ratio = extractDecimal(text,
                    "(?i)(?:minimum|at\\s+least|min(?:imum)?\\s*(?:of)?|>=|≥|>)\\s*(\\d+(?:\\.\\d+)?)");
            Map<String, Object> p = new LinkedHashMap<>();
            if (ratio != null) p.put("minimumRatio", ratio);
            return Optional.of(capabilityMatch("FIN.INTEREST_COVERAGE_MIN", p,
                    ratio != null ? "Interest coverage ≥ " + ratio : "Interest coverage (value missing)",
                    ratio == null));
        }

        // Debt equity
        if (containsAny(lower, "debt to equity", "debt/equity", "d/e", "debt-equity")) {
            BigDecimal ratio = extractDecimal(text,
                    "(?i)(?:not\\s+exceed|maximum|max|<=|≤|upto|up\\s+to)\\s*(\\d+(?:\\.\\d+)?)");
            Map<String, Object> p = new LinkedHashMap<>();
            if (ratio != null) p.put("maximumRatio", ratio);
            return Optional.of(capabilityMatch("FIN.DEBT_EQUITY_MAX", p,
                    ratio != null ? "Debt/Equity ≤ " + ratio : "Debt/Equity (value missing)",
                    ratio == null));
        }

        // TOL/TNW
        if (containsAny(lower, "tol/tnw", "tol to tnw", "total outside liabilities")) {
            BigDecimal ratio = extractDecimal(text,
                    "(?i)(?:not\\s+exceed|maximum|max|<=|≤|upto|up\\s+to)\\s*(\\d+(?:\\.\\d+)?)");
            Map<String, Object> p = new LinkedHashMap<>();
            if (ratio != null) p.put("maximumRatio", ratio);
            return Optional.of(capabilityMatch("FIN.TOL_TNW_MAX", p,
                    ratio != null ? "TOL/TNW ≤ " + ratio : "TOL/TNW (value missing)",
                    ratio == null));
        }

        // ITR income
        if (containsAny(lower, "itr income", "income as per itr")) {
            Long amt = extractMoneyInr(text, lower);
            return Optional.of(capabilityMatch("FIN.ITR_INCOME_MIN",
                    params("minimumAmount", amt),
                    amt != null ? "ITR income ≥ ₹" + amt : "ITR income (value missing)",
                    amt == null));
        }

        // PAT positive
        if (containsAny(lower, "pat", "profit after tax") && containsAny(lower, "positive", "greater than zero", "> 0", ">0")) {
            return Optional.of(capabilityMatch("FIN.PAT_POSITIVE", Map.of(), "PAT must be positive", false));
        }

        // GST turnover (absolute) — not bank/gst %
        if (containsAny(lower, "gst turnover", "annual gst", "gst sales")
                && !containsAny(lower, "banking turnover", "bank turnover", "% of gst")) {
            Long amt = extractMoneyInr(text, lower);
            return Optional.of(capabilityMatch("GST.TURNOVER_MIN",
                    params("minimumAmount", amt),
                    amt != null ? "GST turnover ≥ ₹" + amt : "GST turnover (value missing)",
                    amt == null));
        }

        // Business vintage
        if (containsAny(lower, "business vintage", "years in business", "operated for", "business should have",
                "minimum vintage", "business age")) {
            Long months = extractNumber(text, "(?i)(\\d+)\\s*months?");
            Long years = extractNumber(text, "(?i)(?:at\\s+least|minimum|min(?:imum)?\\s*(?:of)?)\\s*(\\d+)\\s*years?",
                    "(?i)(\\d+)\\s*years?");
            Map<String, Object> p = new LinkedHashMap<>();
            String summary;
            boolean needs;
            if (months != null) {
                p.put("minimumValue", months);
                p.put("unit", "MONTHS");
                summary = "Business vintage ≥ " + months + " months";
                needs = false;
            } else if (years != null) {
                p.put("minimumValue", years);
                p.put("unit", "YEARS");
                summary = "Business vintage ≥ " + years + " years";
                needs = false;
            } else {
                summary = "Business vintage (value missing)";
                needs = true;
            }
            MatchResult base = capabilityMatch("ELIG.BUSINESS_VINTAGE_MIN", p, summary, needs);
            if (months != null) {
                // Preserve months; flag binding note for years-only engines
                Map<String, Object> extraParams = new LinkedHashMap<>(base.extractedParameters());
                return Optional.of(new MatchResult(
                        base.classification(), base.businessCapabilityId(), extraParams,
                        base.catalogueDefaultParameters(), base.needsInput(), base.parameterDiffers(),
                        base.confidence(), base.failureTreatment(),
                        base.rationale() + " Implementation note: months preserved; years conversion may be required for activation.",
                        base.businessSummary(), base.activationIncluded(), null, null));
            }
            return Optional.of(base);
        }

        // Absolute ticket / exposure / facility amount
        if (containsAny(lower, "maximum exposure", "max ticket", "ticket size", "absolute cap",
                "maximum facility", "loan amount shall not", "facility amount")
                || (containsAny(lower, "₹1 crore", "rs 1 crore", "1 crore") && containsAny(lower, "maximum", "cap", "exposure", "ticket"))) {
            Long amt = extractMoneyInr(text, lower);
            return Optional.of(capabilityMatch("LIMIT.ABS_CAP",
                    params("maximumAmount", amt),
                    amt != null ? "Maximum ticket ≤ ₹" + amt : "Maximum ticket (value missing)",
                    amt == null));
        }

        // KYC pass
        if (containsAny(lower, "kyc must", "kyc success", "successful kyc", "kyc completion")) {
            return Optional.of(capabilityMatch("ELIG.REQUIRE_KYC_PASS", Map.of(), "KYC must be successful", false));
        }

        return Optional.empty();
    }

    private MatchResult capabilityMatch(
            String capabilityId,
            Map<String, Object> extracted,
            String summary,
            boolean needsInput) {
        BusinessCapability cap = catalogue.findById(capabilityId).orElse(null);
        Map<String, Object> defaults = new LinkedHashMap<>();
        if (cap != null) {
            for (ParameterDefinition p : cap.parameterDefinitions()) {
                if (p.defaultValue() != null) {
                    defaults.put(p.name(), p.defaultValue());
                }
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        // Precedence: extracted explicit > (not defaults silently for present keys)
        for (Map.Entry<String, Object> e : extracted.entrySet()) {
            if (e.getValue() != null) {
                params.put(e.getKey(), e.getValue());
            }
        }
        boolean usedDefault = false;
        if (cap != null) {
            for (ParameterDefinition p : cap.parameterDefinitions()) {
                if (!params.containsKey(p.name()) && p.defaultValue() != null) {
                    // Only fill defaults when still missing — mark NEEDS_INPUT
                    params.put(p.name(), p.defaultValue());
                    usedDefault = true;
                }
            }
        }
        boolean differs = parameterDiffers(params, defaults);
        IngestionMatchClassification classification;
        if (needsInput || usedDefault && extracted.isEmpty()) {
            classification = IngestionMatchClassification.EXACT_EXISTING_CAPABILITY;
            needsInput = true;
        } else if (differs) {
            classification = IngestionMatchClassification.EXISTING_CAPABILITY_PARAMETER_CHANGE;
        } else {
            classification = IngestionMatchClassification.EXACT_EXISTING_CAPABILITY;
        }
        if (cap != null && cap.manualInputPossible() && containsAny(summary.toLowerCase(Locale.ROOT), "manual")) {
            classification = IngestionMatchClassification.EXISTING_CAPABILITY_MANUAL_DATA;
        }
        String confidence = needsInput ? "MEDIUM" : (extracted.isEmpty() ? "MEDIUM" : "HIGH");
        String treatment = cap != null && !cap.supportedTreatments().isEmpty()
                ? cap.supportedTreatments().get(0) : "REJECT";
        return new MatchResult(
                classification,
                capabilityId,
                params,
                defaults,
                needsInput || usedDefault && extracted.values().stream().allMatch(v -> v == null),
                differs,
                confidence,
                treatment,
                "Matched existing catalogue capability " + capabilityId,
                summary,
                !needsInput,
                null,
                null);
    }

    private boolean parameterDiffers(Map<String, Object> extractedOrMerged, Map<String, Object> defaults) {
        if (defaults == null || defaults.isEmpty()) return false;
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            Object v = extractedOrMerged.get(e.getKey());
            if (v == null) continue;
            if (!numericEquals(v, e.getValue()) && !String.valueOf(v).equalsIgnoreCase(String.valueOf(e.getValue()))) {
                return true;
            }
        }
        return false;
    }

    private boolean numericEquals(Object a, Object b) {
        try {
            return new BigDecimal(String.valueOf(a)).compareTo(new BigDecimal(String.valueOf(b))) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<MatchResult> classifyNonUnderwriting(String text, String lower) {
        if (containsAny(lower, "disclaimer", "for illustration", "authority of", "this policy document",
                "shall be read with", "definitions for the purpose")) {
            return Optional.of(nonExecutable(IngestionMatchClassification.NARRATIVE, text,
                    "Narrative / commentary — not executable", "HIGH", false));
        }
        if (containsAny(lower, "document checklist", "documents required", "copy of pan", "pan card",
                "gst returns", "bank statements", "financial statements", "audited financials",
                "buyer noc", "authority letter", "kyc documents", "submit the following")) {
            return Optional.of(nonExecutable(IngestionMatchClassification.DOCUMENT_REQUIREMENT, text,
                    "Document requirement — checklist only", "HIGH", false));
        }
        if (containsAny(lower, "concentration", "per-anchor", "per anchor", "per-borrower", "per borrower",
                "sector limit", "geography concentration", "portfolio cap")) {
            return Optional.of(nonExecutable(IngestionMatchClassification.PORTFOLIO_CONTROL, text,
                    "Portfolio control — not origination hard rule", "HIGH", false));
        }
        if (containsAny(lower, "sma-", "sma ", "npa", "grace period", "delinquency", "restructuring",
                "repayment routing", "post disbursement", "post-disbursement", "servicing")) {
            return Optional.of(nonExecutable(IngestionMatchClassification.SERVICING_RULE, text,
                    "Servicing rule — excluded from origination activation", "HIGH", false));
        }
        if (containsAny(lower, "facility type", "tenor", "rate cap", "roi cap", "interest rate",
                "program limit", "allowed structure", "scf structure", "product type", "grace for")
                && !containsAny(lower, "bureau", "foir", "dscr", "cheque", "gst turnover ≥", "vintage")) {
            // product config — unless clearly abs cap already handled
            if (!containsAny(lower, "maximum exposure", "absolute cap")) {
                return Optional.of(nonExecutable(IngestionMatchClassification.PRODUCT_CONFIG, text,
                        "Product / configuration clause", "MEDIUM", false));
            }
        }
        if (containsAny(lower, "management quality", "satisfactory", "reputation", "qualitative assessment",
                "at the discretion of", "subjective")) {
            return Optional.of(nonExecutable(IngestionMatchClassification.MANUAL_REVIEW, text,
                    "Human judgement required", "HIGH", false));
        }
        if (containsAny(lower, "promoter experience", "promoter should", "years of experience")
                && !containsAny(lower, "bureau", "business vintage", "operated for")) {
            return Optional.of(manualInput(text, "Promoter experience", "NUMBER"));
        }
        return Optional.empty();
    }

    private MatchResult nonExecutable(
            IngestionMatchClassification classification,
            String text,
            String rationale,
            String confidence,
            boolean activationIncluded) {
        return new MatchResult(
                classification, null, Map.of(), Map.of(), false, false, confidence, null,
                rationale, truncate(text, 160), activationIncluded, null, null);
    }

    private MatchResult manualInput(String text, String label, String type) {
        return new MatchResult(
                IngestionMatchClassification.MANUAL_INPUT, null, Map.of(), Map.of(),
                true, false, "MEDIUM", null,
                "Meaningful clause without automatic fact — propose Manual Input",
                truncate(text, 160), false, label, type);
    }

    private boolean looksLikeManualReview(String lower) {
        return containsAny(lower, "should be satisfactory", "adequate quality", "management assessment");
    }

    private boolean looksLikeManualInput(String lower) {
        return containsAny(lower, "must declare", "officer shall enter", "manual verification of");
    }

    private String inferManualLabel(String text) {
        return truncate(text, 80);
    }

    private static Map<String, Object> params(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (v != null) m.put(k, v);
        return m;
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) return true;
        }
        return false;
    }

    private static Long extractNumber(String text, String... patterns) {
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(text);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static BigDecimal extractDecimal(String text, String... patterns) {
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(text);
            if (m.find()) {
                try {
                    return new BigDecimal(m.group(1));
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static Long extractWindowMonths(String text, String lower) {
        Long m = extractNumber(text, "(?i)(?:preceding|last|past|prior|previous)\\s*(\\d+)\\s*months?",
                "(?i)(\\d+)\\s*months?");
        if (m != null) return m;
        if (lower.contains("three month") || lower.contains("3 month")) return 3L;
        if (lower.contains("twelve month") || lower.contains("12 month") || lower.contains("one year")) return 12L;
        if (lower.contains("six month") || lower.contains("6 month")) return 6L;
        return null;
    }

    private static Long extractMoneyInr(String text, String lower) {
        Matcher crore = Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*(\\d+(?:\\.\\d+)?)\\s*crore").matcher(text);
        if (crore.find()) {
            BigDecimal c = new BigDecimal(crore.group(1)).multiply(new BigDecimal("10000000"));
            return c.setScale(0, RoundingMode.HALF_UP).longValue();
        }
        Matcher lakh = Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*(\\d+(?:\\.\\d+)?)\\s*lakh").matcher(text);
        if (lakh.find()) {
            BigDecimal c = new BigDecimal(lakh.group(1)).multiply(new BigDecimal("100000"));
            return c.setScale(0, RoundingMode.HALF_UP).longValue();
        }
        // 5 Cr shorthand
        Matcher cr = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*cr\\b").matcher(text);
        if (cr.find()) {
            return new BigDecimal(cr.group(1)).multiply(new BigDecimal("10000000"))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
        }
        Matcher plain = Pattern.compile("(?i)(?:₹|rs\\.?|inr)\\s*([\\d,]+)").matcher(text);
        if (plain.find()) {
            try {
                return Long.parseLong(plain.group(1).replace(",", ""));
            } catch (Exception ignored) {
            }
        }
        if (lower.contains("1 crore") || lower.contains("₹1 cr") || lower.contains("rs 1 crore")) {
            return 10_000_000L;
        }
        if (lower.contains("5 crore") || lower.contains("₹5 cr") || lower.contains("5 cr")) {
            return 50_000_000L;
        }
        if (lower.contains("50 lakh") || lower.contains("₹50l") || lower.contains("50l")) {
            return 5_000_000L;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /** Match many clauses; used by tests and SCF fixture reporting. */
    public List<MatchResult> matchAll(List<String> clauses) {
        List<MatchResult> out = new ArrayList<>();
        for (String c : clauses) {
            out.add(match(c));
        }
        return out;
    }
}
