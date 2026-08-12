package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.model.enums.BorrowerType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * POLICY-TYPED-RULE-AUTHORING-1 — typed value controls driven by CanonicalParameterRegistry unit/metadata.
 * Not a new engine: coercion + UI control metadata for CmRuleAuthoringService.
 */
public final class AuthoringValueTypes {

    public static final String CONTROL_BOOLEAN = "BOOLEAN";
    public static final String CONTROL_ENUM = "ENUM";
    public static final String CONTROL_INTEGER = "INTEGER";
    public static final String CONTROL_NUMBER = "NUMBER";
    public static final String CONTROL_PERCENTAGE = "PERCENTAGE";
    public static final String CONTROL_MONEY = "MONEY";
    public static final String CONTROL_DURATION = "DURATION";
    public static final String CONTROL_DATE = "DATE";
    public static final String CONTROL_STRING = "STRING";
    public static final String CONTROL_PARAMETER_REFERENCE = "PARAMETER_REFERENCE";

    private AuthoringValueTypes() {}

    public static String valueControl(CanonicalParameterDefinition def) {
        if (def == null) return CONTROL_NUMBER;
        String unit = def.unit() == null ? "" : def.unit().toUpperCase(Locale.ROOT);
        String id = def.id() == null ? "" : def.id().toLowerCase(Locale.ROOT);
        if ("BOOLEAN".equals(unit) || "FLAG".equals(unit) || id.endsWith(".verified")
                || id.endsWith(".present") || id.contains("ntc") || id.contains("positive")) {
            return CONTROL_BOOLEAN;
        }
        if ("PERCENT".equals(unit) || "PERCENTAGE".equals(unit) || id.contains("foir")
                || id.contains("obligation.ratio") || id.contains("utilisation")
                || id.contains("utilization")) {
            return CONTROL_PERCENTAGE;
        }
        if ("INR".equals(unit) || "MONEY".equals(unit) || "CURRENCY".equals(unit)) {
            return CONTROL_MONEY;
        }
        if ("MONTHS".equals(unit) || "YEARS".equals(unit) || "DAYS".equals(unit)
                || id.contains("vintage") || id.contains("tenure") || id.contains("age_months")
                || id.contains("clean_history")) {
            return CONTROL_DURATION;
        }
        if ("DATE".equals(unit) || id.endsWith("_date") || id.contains(".date")) {
            return CONTROL_DATE;
        }
        if ("CODE".equals(unit) || "ENUM".equals(unit)) {
            return allowedValues(def.id()).isEmpty() ? CONTROL_STRING : CONTROL_ENUM;
        }
        if ("COUNT".equals(unit) || "INTEGER".equals(unit)) {
            return CONTROL_INTEGER;
        }
        if ("SCORE".equals(unit) || id.contains("score") || id.contains("dpd")) {
            return CONTROL_INTEGER;
        }
        return CONTROL_NUMBER;
    }

    public static List<String> operatorsFor(String control) {
        return switch (control == null ? CONTROL_NUMBER : control) {
            case CONTROL_BOOLEAN -> List.of("is", "is not");
            case CONTROL_ENUM -> List.of("is", "is not", "in", "not in");
            case CONTROL_STRING -> List.of("=", "!=", "in", "not in");
            case CONTROL_DATE -> List.of("=", ">=", "<=", ">", "<");
            default -> List.of(">", ">=", "<", "<=", "=", "!=", "in", "not in");
        };
    }

    public static List<Map<String, String>> allowedValues(String parameterId) {
        if (parameterId == null) return List.of();
        // Only actual domain enums — do not invent values
        if ("application.borrower_type".equals(parameterId)) {
            List<Map<String, String>> out = new ArrayList<>();
            for (BorrowerType t : BorrowerType.values()) {
                out.add(Map.of(
                        "value", t.name(),
                        "label", borrowerLabel(t)));
            }
            return out;
        }
        return List.of();
    }

    private static String borrowerLabel(BorrowerType t) {
        return switch (t) {
            case INDIVIDUAL -> "Individual";
            case PROPRIETOR -> "Proprietor";
            case PARTNERSHIP -> "Partnership";
            case COMPANY -> "Company";
        };
    }

    public static List<String> durationUnits(String unit) {
        String u = unit == null ? "MONTHS" : unit.toUpperCase(Locale.ROOT);
        if ("YEARS".equals(u)) return List.of("Years", "Months");
        if ("DAYS".equals(u)) return List.of("Days", "Months");
        return List.of("Months", "Years");
    }

    /**
     * Coerce authoring value to canonical typed form for the parameter.
     * BOOLEAN → Boolean; MONEY/PERCENT/NUMBER → Number; ENUM → String code; DURATION → Number (months).
     */
    public static Object coerce(Object raw, CanonicalParameterDefinition def, String durationUnit) {
        if (raw == null) return null;
        String control = valueControl(def);
        String s = String.valueOf(raw).trim();
        if (s.isBlank() || "null".equalsIgnoreCase(s)) return null;
        return switch (control) {
            case CONTROL_BOOLEAN -> coerceBoolean(raw);
            case CONTROL_ENUM -> coerceEnum(s, def == null ? null : def.id());
            case CONTROL_PERCENTAGE -> coercePercent(s);
            case CONTROL_MONEY -> coerceMoney(s);
            case CONTROL_DURATION -> coerceDuration(s, durationUnit, def == null ? null : def.unit());
            case CONTROL_INTEGER -> coerceInteger(s);
            case CONTROL_DATE -> s; // ISO date string as authored
            case CONTROL_STRING -> s;
            default -> coerceNumber(s);
        };
    }

    public static Object coerceBoolean(Object raw) {
        if (raw instanceof Boolean b) return b;
        if (raw instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (Set.of("yes", "y", "true", "1", "verified", "pass", "passed").contains(s)) return true;
        if (Set.of("no", "n", "false", "0", "unverified", "fail", "failed").contains(s)) return false;
        return null;
    }

    private static Object coerceEnum(String s, String parameterId) {
        List<Map<String, String>> allowed = allowedValues(parameterId);
        if (allowed.isEmpty()) return s;
        String upper = s.toUpperCase(Locale.ROOT).replace(' ', '_');
        for (Map<String, String> opt : allowed) {
            if (opt.get("value").equalsIgnoreCase(s) || opt.get("value").equalsIgnoreCase(upper)
                    || opt.get("label").equalsIgnoreCase(s)) {
                return opt.get("value");
            }
        }
        return null; // invalid enum
    }

    private static Object coercePercent(String s) {
        String cleaned = s.replace("%", "").replace(",", "").trim();
        Object n = coerceNumber(cleaned);
        if (n == null) return null;
        double d = ((Number) n).doubleValue();
        // Studio convention: percent points 0..100 (not 0.50)
        if (d < 0 || d > 100) return null;
        if (d == Math.rint(d)) return (long) d;
        return d;
    }

    private static Object coerceMoney(String s) {
        String cleaned = s
                .replace("₹", "")
                .replace("Rs.", "")
                .replace("Rs", "")
                .replace("INR", "")
                .replace(",", "")
                .replace(" ", "")
                .trim();
        // Indian grouping may leave empty
        return coerceNumber(cleaned);
    }

    private static Object coerceDuration(String s, String durationUnit, String paramUnit) {
        Object n = coerceNumber(s.replaceAll("[^0-9.]+", ""));
        if (n == null) return null;
        double v = ((Number) n).doubleValue();
        String du = durationUnit == null ? "" : durationUnit.toLowerCase(Locale.ROOT);
        String pu = paramUnit == null ? "MONTHS" : paramUnit.toUpperCase(Locale.ROOT);
        // Canonical for MONTHS parameters: store months
        if ("YEARS".equals(pu) || du.startsWith("year")) {
            if (du.startsWith("month") && "YEARS".equals(pu)) {
                v = v / 12.0;
            } else if (du.startsWith("year") && "MONTHS".equals(pu)) {
                v = v * 12.0;
            } else if (du.startsWith("year") && "YEARS".equals(pu)) {
                // keep years
            } else if (du.isBlank() && "MONTHS".equals(pu)) {
                // plain number already months
            }
        } else if (du.startsWith("year") && ("MONTHS".equals(pu) || pu.isBlank())) {
            v = v * 12.0;
        }
        if (v == Math.rint(v)) return (long) v;
        return v;
    }

    private static Object coerceInteger(String s) {
        Object n = coerceNumber(s.replace("%", "").replace(",", ""));
        if (n == null) return null;
        return ((Number) n).longValue();
    }

    public static Object coerceNumber(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n;
        String s = String.valueOf(v).trim().replace("%", "").replace(",", "");
        if (s.isBlank()) return null;
        try {
            if (s.contains(".")) return Double.parseDouble(s);
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static String validationMessage(String control, Object raw) {
        return switch (control == null ? CONTROL_NUMBER : control) {
            case CONTROL_BOOLEAN -> "Choose Yes or No";
            case CONTROL_ENUM -> "Select one of allowed values";
            case CONTROL_PERCENTAGE -> "Invalid percentage (enter 0–100)";
            case CONTROL_MONEY -> "Amount required";
            case CONTROL_DURATION -> "Duration value required";
            case CONTROL_INTEGER, CONTROL_NUMBER -> "Numeric value required";
            case CONTROL_DATE -> "Date required";
            default -> "Value is required";
        };
    }

    public static String formatDisplay(Object value, String control, String unit, String durationUnit) {
        if (value == null) return "?";
        return switch (control == null ? CONTROL_NUMBER : control) {
            case CONTROL_BOOLEAN -> Boolean.TRUE.equals(value) ? "Yes" : "No";
            case CONTROL_PERCENTAGE -> value + "%";
            case CONTROL_MONEY -> "₹" + formatIndian(((Number) value).longValue());
            case CONTROL_DURATION -> {
                String u = durationUnit == null || durationUnit.isBlank()
                        ? (("YEARS".equalsIgnoreCase(unit) ? "years" : "months"))
                        : durationUnit.toLowerCase(Locale.ROOT);
                yield value + " " + u;
            }
            default -> String.valueOf(value);
        };
    }

    /** Indian-style grouping for preview only (persist remains numeric). */
    public static String formatIndian(long amount) {
        String s = Long.toString(Math.abs(amount));
        if (s.length() <= 3) return (amount < 0 ? "-" : "") + s;
        String last3 = s.substring(s.length() - 3);
        String rest = s.substring(0, s.length() - 3);
        StringBuilder sb = new StringBuilder();
        int first = rest.length() % 2;
        if (first > 0) sb.append(rest, 0, first);
        for (int i = first; i < rest.length(); i += 2) {
            if (sb.length() > 0) sb.append(',');
            sb.append(rest, i, Math.min(i + 2, rest.length()));
        }
        if (sb.length() > 0) sb.append(',');
        sb.append(last3);
        return (amount < 0 ? "-" : "") + sb;
    }

    public static String normalizeOperator(String op, String control) {
        if (op == null || op.isBlank()) {
            return CONTROL_BOOLEAN.equals(control) || CONTROL_ENUM.equals(control) ? "is" : ">=";
        }
        String o = op.trim().toLowerCase(Locale.ROOT);
        boolean boolish = CONTROL_BOOLEAN.equals(control) || CONTROL_ENUM.equals(control);
        return switch (o) {
            case "gt", "greater than", "more than", ">" -> ">";
            case "above" -> ">"; // bare "above"; "N and above" handled by CompoundPlainEnglishParser
            case "gte", "ge", "at least", "minimum", ">=", "and above", "& above", "or more", "or higher" -> ">=";
            case "lt", "less than", "<" -> "<";
            case "below" -> "<";
            case "lte", "le", "at most", "not exceed", "no more than", "<=", "and below", "& below" -> "<=";
            case "eq", "equals", "equal", "is", "=" -> boolish ? "is" : "=";
            case "ne", "!=", "is not", "not" -> boolish ? "is not" : "!=";
            default -> op.trim();
        };
    }

    public static Map<String, Object> controlMeta(CanonicalParameterDefinition def) {
        Map<String, Object> m = new LinkedHashMap<>();
        String control = valueControl(def);
        m.put("valueControl", control);
        m.put("operators", operatorsFor(control));
        List<Map<String, String>> allowed = allowedValues(def == null ? null : def.id());
        if (!allowed.isEmpty()) {
            m.put("allowedValues", allowed);
            m.put("valueControl", CONTROL_ENUM);
            m.put("operators", operatorsFor(CONTROL_ENUM));
        }
        if (CONTROL_DURATION.equals(control)) {
            m.put("durationUnits", durationUnits(def == null ? null : def.unit()));
            m.put("showPeriod", false);
            m.put("showDurationUnit", true);
        } else {
            m.put("showPeriod", def != null && def.period() != null
                    && !def.period().isBlank()
                    && !Set.of("SCALAR", "PIT", "PER_TRADELINE", "PER_TRANSACTION", "PER_SCORE",
                    "PER_ENQUIRY", "PER_FACILITY", "PER_RELATIONSHIP", "PER_PERIOD", "PER_YEAR",
                    "PER_MONTH", "CUSTOMER_DEFINED", "LIST").contains(def.period()));
            m.put("showDurationUnit", false);
        }
        m.put("supportsParameterReference", def != null && (
                "banking.avg_daily_balance_3m".equals(def.id())
                        || "banking.monthly_credits_3m".equals(def.id())
                        || "banking.settlement.avg_daily_3m".equals(def.id())));
        return m;
    }
}
