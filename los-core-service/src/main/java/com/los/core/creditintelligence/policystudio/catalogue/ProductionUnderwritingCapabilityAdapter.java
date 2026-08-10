package com.los.core.creditintelligence.policystudio.catalogue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * READ-ONLY adapter: maps production underwriting hardRule / scorecard-style
 * JSON fragments into the universal business capability vocabulary.
 * Does not modify or copy production configuration.
 */
public final class ProductionUnderwritingCapabilityAdapter {

    private static final Pattern COND = Pattern.compile(
            "^(GTE|LTE|GT|LT|EQ|NE)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private ProductionUnderwritingCapabilityAdapter() {}

    /**
     * Map a single production hardRule object (as stored in rules_json.hardRules).
     */
    public static Map<String, Object> mapHardRule(Map<String, Object> hardRule) {
        if (hardRule == null || hardRule.isEmpty()) {
            return Map.of("matched", false, "reason", "empty hardRule");
        }
        String parameter = str(hardRule.get("parameter"));
        String condition = str(hardRule.get("condition"));
        String decision = str(hardRule.get("decision"));
        String id = str(hardRule.get("id"));
        String message = str(hardRule.get("message"));

        MappedCapability mapped = mapParameter(parameter, condition);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "PRODUCTION_UNDERWRITING");
        out.put("sourceKind", ImplementationBinding.PRODUCTION_HARD_RULE);
        out.put("productionRuleId", id.isBlank() ? null : id);
        out.put("productionParameter", parameter.isBlank() ? null : parameter);
        out.put("productionCondition", condition.isBlank() ? null : condition);
        out.put("treatment", normalizeTreatment(decision));
        out.put("message", message.isBlank() ? null : message);
        if (mapped == null || mapped.capabilityId() == null) {
            out.put("matched", false);
            out.put("reason", mapped != null && mapped.note() != null
                    ? mapped.note()
                    : "No safe catalogue mapping for parameter=" + parameter);
            out.put("normalizationSafe", false);
            return out;
        }
        out.put("matched", true);
        out.put("businessCapabilityId", mapped.capabilityId());
        out.put("parameters", mapped.parameters());
        out.put("normalizationSafe", mapped.safe());
        if (mapped.note() != null) {
            out.put("normalizationNote", mapped.note());
        }
        return out;
    }

    /**
     * Map a list of hardRules (e.g. from SCF / product rules_json).
     */
    public static List<Map<String, Object>> mapHardRules(List<Map<String, Object>> hardRules) {
        if (hardRules == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> hr : hardRules) {
            out.add(mapHardRule(hr));
        }
        return out;
    }

    /**
     * Built-in SCF fixture hardRules (from V81 seed text) for adapter demos/tests —
     * does not read the database.
     */
    public static List<Map<String, Object>> scfFixtureHardRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(hr("scf_hard_bureau", "BUREAU_SCORE", "LT:650", "REJECT"));
        rules.add(hr("scf_hard_unsec", "LIVE_UNSECURED_LOAN_COUNT", "GT:6", "REJECT"));
        rules.add(hr("scf_hard_enq", "BUREAU_ENQUIRIES_3M", "GT:21", "REJECT"));
        rules.add(hr("scf_hard_bank_to", "BANKING_TURNOVER_PCT_GST", "LT:75", "REJECT"));
        rules.add(hr("scf_hard_chq3", "CHEQUE_BOUNCES_3M", "GT:0", "REJECT"));
        rules.add(hr("scf_hard_chq12", "CHEQUE_BOUNCES_12M", "GT:6", "REJECT"));
        rules.add(hr("scf_hard_dscr", "DSCR", "LT:1.25", "REJECT"));
        rules.add(hr("scf_hard_ic", "INTEREST_COVERAGE", "LT:1.5", "REJECT"));
        rules.add(hr("scf_hard_de", "DEBT_TO_EQUITY", "GT:2", "REJECT"));
        rules.add(hr("scf_hard_tol", "TOL_TNW", "GT:7", "REJECT"));
        rules.add(hr("scf_hard_gst_to", "ANNUAL_GST_TURNOVER", "LT:50000000", "REJECT"));
        rules.add(hr("scf_hard_biz", "businessStability", "LT:3", "REJECT"));
        rules.add(hr("scf_hard_abs_cap", "REQUESTED_AMOUNT", "GT:10000000", "REJECT"));
        rules.add(hr("scf_hard_pat", "PAT", "LTE:0", "REJECT"));
        rules.add(hr("scf_hard_itr", "ITR_INCOME", "LT:300000", "REJECT"));
        rules.add(hr("scf_hard_cc", "CC_UTILISATION_PCT", "GTE:95", "REJECT"));
        return rules;
    }

    private static Map<String, Object> hr(String id, String parameter, String condition, String decision) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("parameter", parameter);
        m.put("condition", condition);
        m.put("decision", decision);
        return m;
    }

    private static MappedCapability mapParameter(String parameter, String condition) {
        String p = parameter == null ? "" : parameter.trim().toUpperCase(Locale.ROOT);
        Cond c = parseCondition(condition);

        if ("BUREAU_SCORE".equals(p) || "EFFECTIVEBUREAUSCORE".equals(p)) {
            // LT:650 means minimumScore = 650 (fail below)
            Object min = invertMin(c);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("minimumScore", min);
            return new MappedCapability("BUREAU.MIN_SCORE", params, true, null);
        }
        if ("LIVE_UNSECURED_LOAN_COUNT".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumCount", invertMax(c));
            return new MappedCapability("BUREAU.LIVE_UNSECURED_MAX", params, true, null);
        }
        if (p.startsWith("BUREAU_ENQUIRIES")) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumCount", invertMax(c));
            params.put("windowMonths", p.contains("3M") ? 3 : null);
            return new MappedCapability("BUREAU.ENQUIRIES_MAX", params, true, null);
        }
        if ("CHEQUE_BOUNCES_3M".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("windowMonths", 3);
            params.put("maximumCount", invertMax(c));
            return new MappedCapability("BANK.CHEQUE_BOUNCE_MAX", params, true, null);
        }
        if ("CHEQUE_BOUNCES_12M".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("windowMonths", 12);
            params.put("maximumCount", invertMax(c));
            return new MappedCapability("BANK.CHEQUE_BOUNCE_MAX", params, true,
                    "Mapped to BANK.CHEQUE_BOUNCE_MAX with windowMonths=12. "
                            + "Canonical cheque_return_* metrics are related but not auto-merged.");
        }
        if ("BANKING_TURNOVER_PCT_GST".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("minimumPercentage", invertMin(c));
            return new MappedCapability("BANK.TURNOVER_PCT_GST_MIN", params, true, null);
        }
        if ("CC_UTILISATION_PCT".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumPercentage", invertMaxInclusive(c));
            return new MappedCapability("BANK.CC_UTIL_MAX", params, true, null);
        }
        if ("ANNUAL_GST_TURNOVER".equals(p) || "GST_TURNOVER".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("minimumAmount", invertMin(c));
            return new MappedCapability("GST.TURNOVER_MIN", params, true, null);
        }
        if ("BUSINESSSTABILITY".equals(p) || "BUSINESS_STABILITY".equals(p)
                || "businessStability".equalsIgnoreCase(parameter)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("minimumValue", invertMin(c));
            params.put("unit", "YEARS");
            return new MappedCapability("ELIG.BUSINESS_VINTAGE_MIN", params, true,
                    "Production stores vintage in YEARS.");
        }
        if ("DSCR".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("minimumRatio", invertMin(c));
            return new MappedCapability("FIN.DSCR_MIN", params, true, null);
        }
        if ("INTEREST_COVERAGE".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("minimumRatio", invertMin(c));
            return new MappedCapability("FIN.INTEREST_COVERAGE_MIN", params, true, null);
        }
        if ("DEBT_TO_EQUITY".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumRatio", invertMax(c));
            return new MappedCapability("FIN.DEBT_EQUITY_MAX", params, true, null);
        }
        if ("TOL_TNW".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumRatio", invertMax(c));
            return new MappedCapability("FIN.TOL_TNW_MAX", params, true, null);
        }
        if ("ITR_INCOME".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("minimumAmount", invertMin(c));
            return new MappedCapability("FIN.ITR_INCOME_MIN", params, true, null);
        }
        if ("PAT".equals(p)) {
            return new MappedCapability("FIN.PAT_POSITIVE", Map.of(), true, null);
        }
        if ("REQUESTED_AMOUNT".equals(p) || "MAXLOANAMOUNT".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumAmount", invertMax(c));
            String capId = "REQUESTED_AMOUNT".equals(p) ? "LIMIT.ABS_CAP" : "ELIG.MAX_REQUESTED_AMOUNT";
            return new MappedCapability(capId, params, true, null);
        }
        if ("OBLIGATION_RATIO".equals(p) || "FOIR".equals(p) || "FOIR_PERCENT".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumPercentage", invertMax(c));
            return new MappedCapability("FIN.FOIR_MAX", params, true, null);
        }
        if ("LTV".equals(p)) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("maximumPercentage", invertMax(c));
            return new MappedCapability("COLL.LTV_MAX", params, true, null);
        }
        // Inward returns / bounceCount6Months from other stacks — not silently mapped to CHEQUE_BOUNCES
        if (p.contains("INWARD_RETURN") || p.contains("BOUNCECOUNT")) {
            return new MappedCapability(null, Map.of(), false,
                    "Related bounce/return metric — not auto-mapped to BANK.CHEQUE_BOUNCE_MAX without CM confirmation.");
        }
        return null;
    }

    private static Cond parseCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return new Cond("", null);
        }
        Matcher m = COND.matcher(condition.trim());
        if (!m.matches()) {
            return new Cond("", null);
        }
        return new Cond(m.group(1).toUpperCase(Locale.ROOT), parseNumber(m.group(2).trim()));
    }

    /** For LT:X / LTE:X on a minimum-style gate → minimum = X */
    private static Object invertMin(Cond c) {
        if (c.value() == null) {
            return null;
        }
        return c.value();
    }

    /** For GT:X → maximum = X; for GTE:X on util → maximum = X */
    private static Object invertMax(Cond c) {
        return c.value();
    }

    private static Object invertMaxInclusive(Cond c) {
        return c.value();
    }

    private static Object parseNumber(String raw) {
        try {
            if (raw.contains(".")) {
                return new BigDecimal(raw);
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static String normalizeTreatment(String decision) {
        if (decision == null || decision.isBlank()) {
            return "MANUAL_REVIEW";
        }
        String d = decision.trim().toUpperCase(Locale.ROOT);
        if (d.startsWith("REJECT")) {
            return "REJECT";
        }
        if (d.contains("MANUAL")) {
            return "MANUAL_REVIEW";
        }
        if (d.startsWith("APPROVE")) {
            return "APPROVE";
        }
        return d;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private record Cond(String op, Object value) {}

    private record MappedCapability(
            String capabilityId,
            Map<String, Object> parameters,
            boolean safe,
            String note
    ) {}
}
