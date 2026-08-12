package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * POLICY-STUDIO-COMPOUND-RULE-AUTHORING-P0 — lossless compound NL parse + amendments.
 * Does not invent NTC numeric mappings; uses canonical bureau.status_ntc when present.
 */
public final class CompoundPlainEnglishParser {

    private static final Pattern INCLUSIVE_ABOVE = Pattern.compile(
            "(?<![a-z])(-?\\d+(?:\\.\\d+)?)\\s*(?:&|and)\\s*above\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUSIVE_OR_MORE = Pattern.compile(
            "(?<![a-z])(-?\\d+(?:\\.\\d+)?)\\s*(?:or\\s+more|or\\s+higher|or\\s+greater)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_LEAST = Pattern.compile(
            "\\b(?:at\\s+least|minimum(?:\\s+of)?)\\s*(-?\\d+(?:\\.\\d+)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUSIVE_BELOW = Pattern.compile(
            "(?<![a-z])(-?\\d+(?:\\.\\d+)?)\\s*(?:&|and)\\s*below\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUSIVE_ABOVE = Pattern.compile(
            "\\b(?:above|more\\s+than|greater\\s+than)\\s*(-?\\d+(?:\\.\\d+)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUSIVE_BELOW = Pattern.compile(
            "\\b(?:below|less\\s+than|under)\\s*(-?\\d+(?:\\.\\d+)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OF_NUMBER = Pattern.compile(
            "\\bof\\s+(-?\\d+(?:\\.\\d+)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNED_NUMBER = Pattern.compile("(?<!\\d)(-\\d+)\\b");
    private static final Pattern BARE_NUMBER = Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)?)\\b");

    private CompoundPlainEnglishParser() {}

    public static final class ParseResult {
        public boolean compound;
        public boolean complete;
        public String status; // READY | NEEDS_USER_CONFIRMATION | INCOMPLETE
        public String message;
        public String combinator = CompoundExpressionAuthoringSupport.COMBINATOR_ANY;
        public List<Map<String, Object>> conditions = new ArrayList<>();
        public List<String> unresolved = new ArrayList<>();
        public List<String> extractedClauses = new ArrayList<>();
        public String sourceText;
        public Map<String, Object> expression;
        public List<String> previewLines = new ArrayList<>();
        public String ruleDisplay;
        public String treatment = "Reject";
    }

    public static ParseResult parse(String text) {
        ParseResult r = new ParseResult();
        r.sourceText = text == null ? "" : text.trim();
        if (r.sourceText.isBlank()) {
            r.complete = false;
            r.status = "INCOMPLETE";
            r.message = "Rule text is required";
            return r;
        }
        String lower = r.sourceText.toLowerCase(Locale.ROOT);

        // Keep inward-return IF wording on the existing compound-IF path
        if (looksLikeIfBranchWording(lower)) {
            r.compound = true;
            r.complete = false;
            r.status = "NEEDS_USER_CONFIRMATION";
            r.message = "Could not safely interpret this rule — it has multiple branches. "
                    + "Use Build (compound) or Define boundary; do not rewrite from free text.";
            return r;
        }

        boolean bureauCtx = lower.contains("bureau") || lower.contains("cibil")
                || lower.contains("credit score") || lower.contains("bureau score");
        boolean allowOnly = lower.contains("only will be allowed") || lower.contains("will be allowed")
                || lower.contains("only allowed") || lower.contains("are allowed")
                || lower.contains("is allowed");
        boolean altList = countListSeparators(lower) >= 1
                && (allowOnly || lower.contains(" or ") || lower.contains(","));

        List<Map<String, Object>> conditions = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<String> extracted = new ArrayList<>();

        if (bureauCtx && (altList || lower.contains("ntc") || SIGNED_NUMBER.matcher(lower).find())) {
            // Clause: signed sentinel score (e.g. -1)
            Matcher neg = SIGNED_NUMBER.matcher(r.sourceText);
            while (neg.find()) {
                long v = Long.parseLong(neg.group(1));
                extracted.add("Bureau score = " + v);
                conditions.add(scoreCond("=", v));
            }
            // Clause: NTC categorical — never invent numeric mapping
            if (lower.matches(".*\\bntc\\b.*") || lower.contains("new to credit")
                    || lower.contains("new-to-credit") || lower.contains("thin file")
                    || lower.contains("thin-file")) {
                extracted.add("NTC");
                conditions.add(ntcCond());
            }
            // Inclusive / exclusive threshold phrases (consume numbers so they are not re-parsed)
            String residual = r.sourceText;
            residual = consumeThreshold(residual, INCLUSIVE_ABOVE, ">=", conditions, extracted);
            residual = consumeThreshold(residual, INCLUSIVE_OR_MORE, ">=", conditions, extracted);
            residual = consumeThreshold(residual, AT_LEAST, ">=", conditions, extracted);
            residual = consumeThreshold(residual, INCLUSIVE_BELOW, "<=", conditions, extracted);
            residual = consumeThreshold(residual, EXCLUSIVE_ABOVE, ">", conditions, extracted);
            residual = consumeThreshold(residual, EXCLUSIVE_BELOW, "<", conditions, extracted);

            // "Bureau Score of 650" style equality when no comparator phrase remained
            Matcher of = OF_NUMBER.matcher(residual);
            while (of.find()) {
                Object num = parseNum(of.group(1));
                if (num instanceof Number n && n.longValue() >= 0
                        && conditions.stream().noneMatch(c -> sameScoreValue(c, n))) {
                    // Positive "of N" without above/below usually means equality alternative
                    if (altList || allowOnly) {
                        extracted.add("Bureau score = " + formatNum(n));
                        conditions.add(scoreCond("=", n));
                    }
                }
            }

            // Semantic-loss: if text still mentions bare meaningful numbers not mapped
            Matcher bare = BARE_NUMBER.matcher(residual.toLowerCase(Locale.ROOT)
                    .replaceAll("\\blast\\s+\\d+\\s+months?\\b", " ")
                    .replaceAll("\\b\\d+\\s+months?\\b", " "));
            while (bare.find()) {
                Object num = parseNum(bare.group(1));
                if (num instanceof Number n && n.longValue() >= 100
                        && conditions.stream().noneMatch(c -> sameScoreValue(c, n))) {
                    // High score-like number without operator → unresolved (fail closed)
                    unresolved.add(String.valueOf(formatNum(n)));
                    extracted.add(String.valueOf(formatNum(n)));
                }
            }

            r.compound = conditions.size() > 1 || !unresolved.isEmpty();
            r.combinator = CompoundExpressionAuthoringSupport.COMBINATOR_ANY;
            r.conditions = dedupe(conditions);
            r.extractedClauses = extracted;
            r.unresolved = unresolved;
            if (!unresolved.isEmpty()) {
                r.complete = false;
                r.status = "NEEDS_USER_CONFIRMATION";
                r.message = "Some parts of this rule have not been mapped yet.";
            } else if (r.conditions.isEmpty()) {
                r.complete = false;
                r.status = "INCOMPLETE";
                r.message = "Could not interpret bureau conditions from this text.";
            } else {
                r.complete = true;
                r.status = "READY";
                r.message = "Ready to confirm";
                r.expression = CompoundExpressionAuthoringSupport.buildExpression(
                        r.combinator, r.conditions);
                r.previewLines = CompoundExpressionAuthoringSupport.previewLines(
                        r.combinator, r.conditions);
                r.ruleDisplay = CompoundExpressionAuthoringSupport.businessSummary(
                        r.combinator, r.conditions);
            }
            return r;
        }

        // Generic multi-alternative numeric thresholds on a single matched parameter domain
        List<BoundOp> bounds = extractBounds(r.sourceText);
        if (bounds.size() >= 2) {
            r.compound = true;
            r.combinator = CompoundExpressionAuthoringSupport.COMBINATOR_ANY;
            // Without a clear parameter, fail closed
            r.complete = false;
            r.status = "NEEDS_USER_CONFIRMATION";
            r.message = "Some parts of this rule have not been mapped yet.";
            for (BoundOp b : bounds) {
                r.extractedClauses.add(b.op + " " + b.value);
                r.unresolved.add(b.op + " " + b.value);
            }
            return r;
        }

        r.compound = false;
        r.complete = false;
        r.status = "INCOMPLETE";
        r.message = "Not a compound alternative rule";
        return r;
    }

    /**
     * Apply incremental NL amendment against an existing proposed group model.
     */
    @SuppressWarnings("unchecked")
    public static ParseResult amend(Map<String, Object> proposed, String amendmentText) {
        ParseResult r = new ParseResult();
        r.sourceText = amendmentText == null ? "" : amendmentText.trim();
        if (proposed == null || !(proposed.get("conditions") instanceof List<?>)) {
            return parse(amendmentText);
        }
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (Object o : (List<?>) proposed.get("conditions")) {
            if (o instanceof Map<?, ?> m) conditions.add(new LinkedHashMap<>((Map<String, Object>) m));
        }
        String combinator = String.valueOf(proposed.getOrDefault("combinator",
                CompoundExpressionAuthoringSupport.COMBINATOR_ANY));
        String lower = r.sourceText.toLowerCase(Locale.ROOT);

        if (lower.contains("keep everything else") || lower.contains("keep the rest")
                || lower.contains("unchanged")) {
            // fall through after applying other ops
        }

        if (lower.startsWith("also ") || lower.contains("also add") || lower.contains("i also want")
                || lower.contains("add ") && (lower.contains("also") || lower.contains("want to add"))) {
            ParseResult add = parse(stripAlsoPrefix(r.sourceText));
            if (!add.unresolved.isEmpty() && add.conditions.isEmpty()) {
                r.complete = false;
                r.status = "NEEDS_USER_CONFIRMATION";
                r.unresolved = add.unresolved;
                r.message = "Some parts of this rule have not been mapped yet.";
                r.conditions = conditions;
                r.combinator = combinator;
                return r;
            }
            for (Map<String, Object> c : add.conditions) {
                if (conditions.stream().noneMatch(x -> sameCond(x, c))) conditions.add(c);
            }
            combinator = CompoundExpressionAuthoringSupport.COMBINATOR_ANY;
        } else if (lower.contains("remove ") || lower.startsWith("except ")) {
            ParseResult rem = parse(r.sourceText.replaceFirst("(?i)\\b(remove|except)\\b", "").trim());
            conditions.removeIf(c -> rem.conditions.stream().anyMatch(x -> sameCond(x, c))
                    || rem.extractedClauses.stream().anyMatch(ex -> matchesRemoval(c, ex, lower)));
            if (lower.contains("ntc")) {
                conditions.removeIf(c -> CompoundExpressionAuthoringSupport.NTC_FACT
                        .equals(c.get("parameterId")));
            }
        } else if (lower.matches(".*\\bchange\\s+(-?\\d+(?:\\.\\d+)?)\\s+to\\s+(-?\\d+(?:\\.\\d+)?)\\b.*")) {
            Matcher m = Pattern.compile("\\bchange\\s+(-?\\d+(?:\\.\\d+)?)\\s+to\\s+(-?\\d+(?:\\.\\d+)?)\\b",
                    Pattern.CASE_INSENSITIVE).matcher(lower);
            if (m.find()) {
                Object from = parseNum(m.group(1));
                Object to = parseNum(m.group(2));
                for (Map<String, Object> c : conditions) {
                    if (sameScoreValue(c, from)) c.put("value", to);
                }
            }
        } else if (lower.contains("make it greater than") || lower.contains("make it more than")
                || lower.contains("make it above")) {
            Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(lower);
            Object n = m.find() ? parseNum(m.group(1)) : null;
            for (Map<String, Object> c : conditions) {
                if (CompoundExpressionAuthoringSupport.BUREAU_SCORE.equals(c.get("parameterId"))
                        && (">=".equals(c.get("operator")) || ">".equals(c.get("operator"))
                        || (n != null && sameScoreValue(c, n)))) {
                    c.put("operator", ">");
                    if (n != null) c.put("value", n);
                }
            }
        } else if (lower.contains("make it") && (lower.contains("and above") || lower.contains("& above")
                || lower.contains("at least"))) {
            Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(lower);
            Object n = m.find() ? parseNum(m.group(1)) : null;
            for (Map<String, Object> c : conditions) {
                if (CompoundExpressionAuthoringSupport.BUREAU_SCORE.equals(c.get("parameterId"))
                        && (">=".equals(c.get("operator")) || ">".equals(c.get("operator"))
                        || (n != null && sameScoreValue(c, n)))) {
                    c.put("operator", ">=");
                    if (n != null) c.put("value", n);
                }
            }
        } else if (lower.contains("instead")) {
            ParseResult neu = parse(r.sourceText.replaceFirst("(?i).*\\binstead\\b", "").trim());
            if (neu.complete) {
                conditions = neu.conditions;
                combinator = neu.combinator;
            } else {
                r.complete = false;
                r.status = "NEEDS_USER_CONFIRMATION";
                r.message = "Some parts of this rule have not been mapped yet.";
                r.unresolved = neu.unresolved.isEmpty() ? List.of(r.sourceText) : neu.unresolved;
                r.conditions = conditions;
                r.combinator = combinator;
                return r;
            }
        } else {
            // Treat as full reparse amendment when it looks like a complete rule statement
            ParseResult neu = parse(r.sourceText);
            if (neu.compound && neu.complete) {
                return neu;
            }
            // Try merge parse fragments into existing
            ParseResult add = parse(r.sourceText);
            for (Map<String, Object> c : add.conditions) {
                if (conditions.stream().noneMatch(x -> sameCond(x, c))) conditions.add(c);
            }
            if (!add.unresolved.isEmpty()) {
                r.unresolved.addAll(add.unresolved);
            }
        }

        r.compound = true;
        r.combinator = combinator;
        r.conditions = dedupe(conditions);
        if (!r.unresolved.isEmpty()) {
            r.complete = false;
            r.status = "NEEDS_USER_CONFIRMATION";
            r.message = "Some parts of this rule have not been mapped yet.";
            return r;
        }
        if (r.conditions.isEmpty()) {
            r.complete = false;
            r.status = "INCOMPLETE";
            r.message = "No conditions remain after amendment.";
            return r;
        }
        r.complete = true;
        r.status = "READY";
        r.message = "Ready to confirm";
        r.expression = CompoundExpressionAuthoringSupport.buildExpression(r.combinator, r.conditions);
        r.previewLines = CompoundExpressionAuthoringSupport.previewLines(r.combinator, r.conditions);
        r.ruleDisplay = CompoundExpressionAuthoringSupport.businessSummary(r.combinator, r.conditions);
        return r;
    }

    /** Detect lossy flat authoring risk for DESCRIBE sentences with multiple business clauses. */
    public static boolean looksLikeMultiClause(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (looksLikeIfBranchWording(lower)) return true;
        boolean bureau = lower.contains("bureau") || lower.contains("cibil") || lower.contains("score");
        if (bureau && (lower.matches(".*\\bntc\\b.*") || SIGNED_NUMBER.matcher(text).find())
                && (lower.contains("above") || lower.contains("at least") || lower.contains("or more")
                || BARE_NUMBER.matcher(lower).find())) {
            return true;
        }
        return extractBounds(text).size() >= 2;
    }

    public static String detectBoundaryOperator(String phrase) {
        if (phrase == null) return null;
        String lower = phrase.toLowerCase(Locale.ROOT);
        if (INCLUSIVE_ABOVE.matcher(lower).find() || INCLUSIVE_OR_MORE.matcher(lower).find()
                || AT_LEAST.matcher(lower).find()
                || lower.contains("and above") || lower.contains("& above")
                || lower.contains("or more") || lower.contains("or higher")) {
            return ">=";
        }
        if (INCLUSIVE_BELOW.matcher(lower).find() || lower.contains("and below") || lower.contains("& below")) {
            return "<=";
        }
        if (EXCLUSIVE_ABOVE.matcher(lower).find()
                || lower.matches(".*\\b(above|more than|greater than)\\s*-?\\d+.*")) {
            return ">";
        }
        if (EXCLUSIVE_BELOW.matcher(lower).find()) {
            return "<";
        }
        return null;
    }

    private static boolean looksLikeIfBranchWording(String lower) {
        boolean multiIf = lower.split("\\bif\\b").length > 2;
        boolean semiBranches = lower.contains(";") && (lower.contains("if <") || lower.contains("if >")
                || lower.contains("if ≤") || lower.contains("if ≥") || lower.contains("if <=")
                || lower.contains("if >="));
        boolean ratioAndCount = (lower.contains("ratio") || lower.contains("%"))
                && lower.contains("count")
                && (lower.contains("100") || lower.contains("transaction"));
        boolean colonBranches = lower.contains(":") && lower.contains(";")
                && (lower.contains("return") || lower.contains("ratio"));
        return multiIf || semiBranches || ratioAndCount || colonBranches;
    }

    private static int countListSeparators(String lower) {
        int n = 0;
        for (int i = 0; i < lower.length(); i++) {
            if (lower.charAt(i) == ',') n++;
        }
        if (lower.contains(" and ")) n++;
        if (lower.contains(" or ")) n++;
        return n;
    }

    private static String consumeThreshold(
            String text, Pattern p, String op,
            List<Map<String, Object>> conditions, List<String> extracted) {
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Object num = parseNum(m.group(1));
            extracted.add("Bureau score " + op + " " + formatNum(num));
            if (conditions.stream().noneMatch(c -> sameScoreValue(c, num) && op.equals(c.get("operator")))) {
                conditions.add(scoreCond(op, num));
            }
            m.appendReplacement(sb, " ");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static List<BoundOp> extractBounds(String text) {
        List<BoundOp> out = new ArrayList<>();
        for (Pattern p : List.of(INCLUSIVE_ABOVE, INCLUSIVE_OR_MORE, AT_LEAST)) {
            Matcher m = p.matcher(text);
            while (m.find()) out.add(new BoundOp(">=", parseNum(m.group(1))));
        }
        Matcher m = EXCLUSIVE_ABOVE.matcher(text);
        while (m.find()) out.add(new BoundOp(">", parseNum(m.group(1))));
        m = INCLUSIVE_BELOW.matcher(text);
        while (m.find()) out.add(new BoundOp("<=", parseNum(m.group(1))));
        m = EXCLUSIVE_BELOW.matcher(text);
        while (m.find()) out.add(new BoundOp("<", parseNum(m.group(1))));
        return out;
    }

    private static Map<String, Object> scoreCond(String op, Object value) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("parameterId", CompoundExpressionAuthoringSupport.BUREAU_SCORE);
        c.put("parameterName", "Bureau score");
        c.put("operator", op);
        c.put("value", value);
        c.put("leftKind", "METRIC");
        c.put("valueControl", AuthoringValueTypes.CONTROL_NUMBER);
        return c;
    }

    private static Map<String, Object> ntcCond() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("parameterId", CompoundExpressionAuthoringSupport.NTC_FACT);
        c.put("parameterName", "Bureau status");
        c.put("operator", "=");
        c.put("value", true);
        c.put("leftKind", "FACT");
        c.put("valueControl", AuthoringValueTypes.CONTROL_BOOLEAN);
        c.put("valueLabel", "NTC");
        return c;
    }

    private static List<Map<String, Object>> dedupe(List<Map<String, Object>> in) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : in) {
            if (out.stream().noneMatch(x -> sameCond(x, c))) out.add(c);
        }
        return out;
    }

    private static boolean sameCond(Map<String, Object> a, Map<String, Object> b) {
        return String.valueOf(a.get("parameterId")).equals(String.valueOf(b.get("parameterId")))
                && String.valueOf(a.get("operator")).equals(String.valueOf(b.get("operator")))
                && ObjectsEquals(a.get("value"), b.get("value"));
    }

    private static boolean sameScoreValue(Map<String, Object> c, Object n) {
        if (!CompoundExpressionAuthoringSupport.BUREAU_SCORE.equals(c.get("parameterId"))) return false;
        return ObjectsEquals(c.get("value"), n);
    }

    private static boolean ObjectsEquals(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        return java.util.Objects.equals(a, b);
    }

    private static boolean matchesRemoval(Map<String, Object> c, String extracted, String lower) {
        if (extracted == null) return false;
        String e = extracted.toLowerCase(Locale.ROOT);
        if (e.contains("ntc") && CompoundExpressionAuthoringSupport.NTC_FACT.equals(c.get("parameterId"))) {
            return true;
        }
        Matcher m = Pattern.compile("(-?\\d+)").matcher(e);
        while (m.find()) {
            if (sameScoreValue(c, parseNum(m.group(1)))) return true;
        }
        return false;
    }

    private static String stripAlsoPrefix(String text) {
        return text.replaceFirst("(?i)^\\s*(i\\s+also\\s+want\\s+to\\s+add|also\\s+add|also|add)\\s+", "")
                .trim();
    }

    private static Object parseNum(String s) {
        if (s == null) return null;
        double d = Double.parseDouble(s);
        if (d == Math.rint(d)) return (long) d;
        return d;
    }

    private static Object formatNum(Object n) {
        if (n instanceof Number num && num.doubleValue() == Math.rint(num.doubleValue())) {
            return num.longValue();
        }
        return n;
    }

    private record BoundOp(String op, Object value) {}
}
