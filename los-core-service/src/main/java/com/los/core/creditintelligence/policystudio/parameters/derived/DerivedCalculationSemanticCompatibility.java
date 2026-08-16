package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Semantic / dimensional compatibility for derived-calculation research and Accept.
 * Overlapping aliases or primitives alone never authorise READY_FOR_REVIEW or Accept.
 * A direct REF requires strong equivalence across unit, temporal, aggregation, and meaning.
 */
public final class DerivedCalculationSemanticCompatibility {

    private DerivedCalculationSemanticCompatibility() {}

    public record Result(
            boolean compatible,
            boolean strongEquivalence,
            List<String> failures,
            List<String> evidence
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("compatible", compatible);
            m.put("strongEquivalence", strongEquivalence);
            m.put("failures", failures);
            m.put("evidence", evidence);
            return m;
        }
    }

    /**
     * Assess whether {@code source} can stand in for {@code target} under the given transform.
     * @param directRef true when expression is a single REF to source (strictest bar)
     */
    public static Result assess(
            CanonicalParameterDefinition target,
            CanonicalParameterDefinition source,
            boolean directRef) {
        List<String> failures = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        if (target == null || source == null) {
            failures.add("Missing target or source definition");
            return new Result(false, false, failures, evidence);
        }

        // --- datatype / unit / dimension ---
        String tu = dimFamily(target.unit());
        String su = dimFamily(source.unit());
        if (!tu.isBlank() && !su.isBlank() && !tu.equals(su)) {
            failures.add("Unit/dimension mismatch: target=" + norm(target.unit())
                    + " (" + tu + ") vs source=" + norm(source.unit()) + " (" + su + ")");
        } else if (!tu.isBlank() && tu.equals(su)) {
            evidence.add("Unit/dimension compatible: " + tu);
        } else if (tu.isBlank() || su.isBlank()) {
            if (directRef) {
                failures.add("Direct REF requires known unit/dimension on both target and source");
            }
        }

        // --- temporal meaning / window ---
        String tp = temporalFamily(target.period());
        String sp = temporalFamily(source.period());
        if (!tp.isBlank() && !sp.isBlank() && !temporalCompatible(tp, sp)) {
            failures.add("Temporal/window mismatch: target period=" + norm(target.period())
                    + " vs source period=" + norm(source.period()));
        } else if (!tp.isBlank() && tp.equals(sp)) {
            evidence.add("Temporal period compatible: " + tp);
        } else if (directRef && (!tp.isBlank() || !sp.isBlank()) && !tp.equals(sp)
                && !temporalCompatible(tp, sp)) {
            failures.add("Direct REF requires matching temporal semantics");
        }

        // CUSTOMER_DEFINED / vocabulary-gated targets cannot be satisfied by an arbitrary REF
        String missing = target.capability() == null ? ""
                : String.valueOf(target.capability().missingDataTreatment());
        String missingU = missing.toUpperCase(Locale.ROOT);
        if (missingU.contains("NEEDS_CONFIGURATION")
                || missingU.contains("CALCULATION_NOT_IMPLEMENTED")
                || missingU.contains("VOCABULARY")
                || "CUSTOMER_DEFINED".equals(norm(target.period()))) {
            if (directRef) {
                failures.add("Target requires CM vocabulary / configuration; a direct REF cannot "
                        + "encode customer-defined clean-history (or equivalent) semantics");
            }
        }

        // --- aggregation meaning ---
        String ta = aggregationOf(target);
        String sa = aggregationOf(source);
        if (!ta.isBlank() && !sa.isBlank() && !ta.equals(sa)) {
            failures.add("Aggregation mismatch: target=" + ta + " vs source=" + sa);
        } else if (!ta.isBlank() && ta.equals(sa)) {
            evidence.add("Aggregation compatible: " + ta);
        } else if (directRef && !ta.isBlank() && sa.isBlank()) {
            failures.add("Direct REF requires known aggregation on source when target declares one");
        }

        // --- business semantics (strong for REF) ---
        boolean businessEq = businessSemanticEquivalence(target, source, evidence, failures, directRef);
        boolean strong = failures.isEmpty() && businessEq
                && unitEqualOrBothBlank(target.unit(), source.unit())
                && (tp.isBlank() || tp.equals(sp) || temporalCompatible(tp, sp));

        if (directRef && !strong && failures.isEmpty()) {
            failures.add("Direct REF requires strong semantic equivalence; overlapping primitives/"
                    + "aliases alone are insufficient");
        }

        boolean compatible = failures.isEmpty() && (!directRef || strong);
        return new Result(compatible, strong && failures.isEmpty(), failures, evidence);
    }

    /**
     * Validate every REF leaf in an expression against the target's semantic identity.
     * Constructed ops (MONTHS_SINCE_LAST_MATCH) are validated as history→months transforms,
     * not as direct REF equivalence to HISTORY unit.
     */
    public static Result assessExpression(
            CanonicalParameterDefinition target,
            Map<String, Object> expression,
            java.util.function.Function<String, CanonicalParameterDefinition> resolve) {
        if (isMonthsSinceLastMatch(expression)) {
            return assessMonthsSinceLastMatch(target, expression, resolve);
        }
        Set<String> deps = SafeDerivedExpressionEvaluator.collectDependencies(expression);
        boolean directRef = isDirectRef(expression);
        List<String> failures = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        if (deps.isEmpty()) {
            failures.add("Expression has no GACAT REF dependencies");
            return new Result(false, false, failures, evidence);
        }
        for (String depId : deps) {
            CanonicalParameterDefinition src = resolve.apply(depId);
            if (src == null) {
                failures.add("Unknown dependency: " + depId);
                continue;
            }
            Result r = assess(target, src, directRef && deps.size() == 1);
            evidence.addAll(r.evidence());
            if (!r.compatible()) {
                for (String f : r.failures()) {
                    failures.add(depId + ": " + f);
                }
            }
        }
        // Multi-op expressions still need unit-compatible leaves; direct REF already gated above.
        boolean compatible = failures.isEmpty();
        return new Result(compatible, compatible && directRef, failures, evidence);
    }

    static boolean isMonthsSinceLastMatch(Map<String, Object> expression) {
        if (expression == null || expression.isEmpty()) return false;
        String op = String.valueOf(expression.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        return "MONTHS_SINCE_LAST_MATCH".equals(op);
    }

    private static Result assessMonthsSinceLastMatch(
            CanonicalParameterDefinition target,
            Map<String, Object> expression,
            java.util.function.Function<String, CanonicalParameterDefinition> resolve) {
        List<String> failures = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        String tu = dimFamily(target.unit());
        if (!"DURATION_CALENDAR".equals(tu) && !"COUNT".equals(tu) && !tu.isBlank()) {
            failures.add("MONTHS_SINCE_LAST_MATCH requires MONTHS (or count) target unit; got "
                    + norm(target.unit()));
        } else {
            evidence.add("Target unit compatible with months-since transform: " + norm(target.unit()));
        }
        Set<String> deps = SafeDerivedExpressionEvaluator.collectDependencies(expression);
        if (deps.isEmpty()) {
            failures.add("MONTHS_SINCE_LAST_MATCH requires a history REF dependency");
        }
        for (String depId : deps) {
            if (BusinessCalculationAssistant.isMaxDpdProxyId(depId)) {
                failures.add("max_dpd proxy rejected as clean-history input: " + depId);
                continue;
            }
            CanonicalParameterDefinition src = resolve.apply(depId);
            if (src == null) {
                failures.add("Unknown dependency: " + depId);
                continue;
            }
            String su = dimFamily(src.unit());
            if (!"HISTORY_SERIES".equals(su) && !"DURATION_DAYS".equals(su)
                    && !"COUNT".equals(su) && !su.isBlank()) {
                failures.add(depId + ": expected HISTORY (dated DPD series), got " + norm(src.unit()));
            } else {
                evidence.add(depId + ": acceptable history/observation input (" + norm(src.unit()) + ")");
            }
            boolean impl = src.capability() != null && src.capability().implemented();
            if (!impl) {
                failures.add(depId + ": input is not implemented in catalogue");
            }
        }
        Object asOf = expression.get("asOf");
        if (!(asOf instanceof Map<?, ?> asOfMap)
                || !"EVAL_AS_OF".equalsIgnoreCase(String.valueOf(asOfMap.get("op")))) {
            failures.add("MONTHS_SINCE_LAST_MATCH requires asOf EVAL_AS_OF (deterministic evaluation date)");
        } else {
            evidence.add("Evaluation date authority: EVAL_AS_OF");
        }
        if (!expression.containsKey("matchValue")) {
            failures.add("matchValue required");
        }
        boolean compatible = failures.isEmpty();
        return new Result(compatible, false, failures, evidence);
    }

    public static boolean isDirectRef(Map<String, Object> expression) {
        if (expression == null || expression.isEmpty()) return false;
        String op = String.valueOf(expression.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        return "REF".equals(op) && expression.get("id") != null;
    }

    private static boolean businessSemanticEquivalence(
            CanonicalParameterDefinition target,
            CanonicalParameterDefinition source,
            List<String> evidence,
            List<String> failures,
            boolean directRef) {
        String tName = normWords(target.businessName());
        String sName = normWords(source.businessName());
        String tSum = normWords(target.calculationSummary());
        String sSum = normWords(source.calculationSummary());

        // Identity / alias exact match
        Set<String> tAliases = aliasSet(target);
        Set<String> sAliases = aliasSet(source);
        boolean aliasExact = false;
        for (String a : tAliases) {
            if (sAliases.contains(a) || a.equals(sName) || a.equals(normWords(source.id()))) {
                aliasExact = true;
                break;
            }
        }
        if (aliasExact || tName.equals(sName)) {
            evidence.add("Business name/alias equivalence");
            return true;
        }

        // Shared meaningful tokens beyond stopwords / generic bureau terms
        Set<String> tTokens = meaningfulTokens(tName + " " + tSum);
        Set<String> sTokens = meaningfulTokens(sName + " " + sSum);
        long overlap = tTokens.stream().filter(sTokens::contains).count();
        // Conflicting concept tokens: clean-history vs max-dpd style
        if (conflicts(tTokens, sTokens)) {
            failures.add("Business semantics conflict between target (" + target.businessName()
                    + ") and source (" + source.businessName() + ")");
            return false;
        }
        if (overlap >= 2 && tTokens.size() >= 2) {
            evidence.add("Shared semantic tokens: overlap=" + overlap);
            return !directRef; // multi-op discovery may use soft match; REF still needs stronger
        }
        if (directRef) {
            failures.add("Insufficient business-semantic equivalence for direct REF of "
                    + source.id() + " as " + target.id());
        }
        return false;
    }

    private static boolean conflicts(Set<String> t, Set<String> s) {
        // clean/history/months vs max/dpd/days are opposing credit concepts when co-present
        boolean tClean = t.contains("clean") || t.contains("history");
        boolean sDpd = s.contains("dpd") || s.contains("past") || (s.contains("maximum") && s.contains("days"));
        boolean tMonths = t.contains("months") || t.contains("month");
        boolean sDays = s.contains("days") || s.contains("day");
        if (tClean && sDpd) return true;
        if (tMonths && sDays && (tClean || sDpd)) return true;
        return false;
    }

    private static Set<String> meaningfulTokens(String text) {
        Set<String> stop = Set.of(
                "the", "a", "an", "of", "and", "or", "to", "for", "in", "on", "by", "with",
                "from", "after", "across", "highest", "whether", "months", "month", "days", "day",
                "bureau", "retail", "parameter", "value", "credit", "must", "be", "confirmed",
                "definition", "customer", "defined", "trailing");
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() < 3 || stop.contains(w)) continue;
            out.add(w);
        }
        return out;
    }

    private static Set<String> aliasSet(CanonicalParameterDefinition d) {
        Set<String> s = new java.util.LinkedHashSet<>();
        if (d.aliases() != null) {
            for (String a : d.aliases()) {
                if (a != null) s.add(normWords(a));
            }
        }
        return s;
    }

    private static String aggregationOf(CanonicalParameterDefinition d) {
        if (d.capability() == null || d.capability().aggregation() == null) return "";
        return norm(d.capability().aggregation());
    }

    /** Map units into dimension families. */
    static String dimFamily(String unit) {
        String u = norm(unit);
        if (u.isBlank()) return "";
        return switch (u) {
            case "MONTHS", "MONTH", "YEARS", "YEAR" -> "DURATION_CALENDAR";
            case "DAYS", "DAY" -> "DURATION_DAYS";
            case "INR", "MONEY", "AMOUNT", "CURRENCY" -> "MONEY";
            case "PERCENT", "RATIO", "PCT" -> "RATIO";
            case "COUNT", "NUMBER", "INTEGER", "SCALAR" -> "COUNT";
            case "BOOLEAN", "FLAG" -> "BOOLEAN";
            case "CODE", "ENUM", "STRING", "TEXT" -> "CODE";
            case "HISTORY" -> "HISTORY_SERIES";
            default -> u;
        };
    }

    static String temporalFamily(String period) {
        String p = norm(period);
        if (p.isBlank()) return "";
        if (p.contains("TRAILING_6")) return "TRAILING_6M";
        if (p.contains("TRAILING_12")) return "TRAILING_12M";
        if (p.contains("TRAILING_24")) return "TRAILING_24M";
        if (p.contains("CUSTOMER_DEFINED") || p.contains("VOCABULARY")) return "CUSTOMER_DEFINED";
        if (p.contains("PER_MONTH") || p.contains("PER_TRADELINE_PER_MONTH")) return "PER_MONTH";
        if (p.contains("PIT") || p.contains("POINT")) return "PIT";
        if (p.contains("CURRENT")) return "CURRENT";
        return p;
    }

    private static boolean temporalCompatible(String a, String b) {
        if (a.equals(b)) return true;
        // Trailing windows of different lengths are not compatible for REF
        if (a.startsWith("TRAILING_") && b.startsWith("TRAILING_")) return false;
        // Customer-defined never compatible with fixed trailing window
        if ("CUSTOMER_DEFINED".equals(a) || "CUSTOMER_DEFINED".equals(b)) return false;
        return false;
    }

    private static boolean unitEqualOrBothBlank(String a, String b) {
        String na = norm(a);
        String nb = norm(b);
        if (na.isBlank() || nb.isBlank()) return false;
        return dimFamily(a).equals(dimFamily(b));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String normWords(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
