package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * POLICY-STUDIO-AUTHORING-COMPLETENESS-GATE-1 — lossless compound NL parse,
 * nested AND/OR, IN/NOT IN, amendments, fail-closed ambiguity.
 */
public final class CompoundPlainEnglishParser {

    private static final Pattern INCLUSIVE_ABOVE = Pattern.compile(
            "(?<![a-z])(-?\\d+(?:\\.\\d+)?)\\s*%?\\s*(?:&|and)\\s*above\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUSIVE_OR_MORE = Pattern.compile(
            "(?<![a-z])(-?\\d+(?:\\.\\d+)?)\\s*%?\\s*(?:or\\s+more|or\\s+higher|or\\s+greater|or\\s+above)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_LEAST = Pattern.compile(
            "\\b(?:at\\s+least|minimum(?:\\s+of)?)\\s*(-?\\d+(?:\\.\\d+)?)\\s*%?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCLUSIVE_BELOW = Pattern.compile(
            "(?<![a-z])(-?\\d+(?:\\.\\d+)?)\\s*%?\\s*(?:&|and|or)\\s*below\\b"
                    + "|\\b(?:at\\s+most|not\\s+exceed(?:ing)?)\\s*(-?\\d+(?:\\.\\d+)?)\\s*%?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUSIVE_ABOVE = Pattern.compile(
            "\\b(?:above|more\\s+than|greater\\s+than)\\s*(-?\\d+(?:\\.\\d+)?)\\s*%?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUSIVE_BELOW = Pattern.compile(
            "\\b(?:below|less\\s+than|under)\\s*(-?\\d+(?:\\.\\d+)?)\\s*%?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNED_NUMBER = Pattern.compile("(?<!\\d)(-\\d+)\\b");
    private static final Pattern VAGUE = Pattern.compile(
            "\\b(good|high|low|around|maybe|something|satisfactory|adequate|decent|nice)\\b",
            Pattern.CASE_INSENSITIVE);

    private CompoundPlainEnglishParser() {}

    public static final class ParseResult {
        public boolean compound;
        public boolean complete;
        public String status; // READY | NEEDS_CLARIFICATION | INCOMPLETE
        public String message;
        public String combinator = CompoundExpressionAuthoringSupport.COMBINATOR_ANY;
        public List<Map<String, Object>> conditions = new ArrayList<>();
        public List<Map<String, Object>> children = new ArrayList<>();
        public List<String> unresolved = new ArrayList<>();
        public List<String> understood = new ArrayList<>();
        public List<String> extractedClauses = new ArrayList<>();
        public String sourceText;
        public Map<String, Object> expression;
        public Map<String, Object> editableModel;
        public List<String> previewLines = new ArrayList<>();
        public String ruleDisplay;
        public String treatment = "Reject";
    }

    public static ParseResult parse(String text) {
        ParseResult r = new ParseResult();
        r.sourceText = text == null ? "" : text.trim();
        if (r.sourceText.isBlank()) {
            return incomplete(r, "Rule text is required");
        }
        String lower = r.sourceText.toLowerCase(Locale.ROOT);

        if (looksLikeIfBranchWording(lower)) {
            r.compound = true;
            return needsClarification(r,
                    List.of("Multi-branch IF wording"),
                    List.of("Use Build (compound IF) or Define boundary — free-text rewrite is unsafe."));
        }

        if (isAdversarialVague(lower)) {
            return needsClarification(r,
                    List.of(),
                    List.of(r.sourceText + " — not a measurable policy condition"));
        }

        // C6 / C7 enumerations first
        ParseResult enumResult = parseEnumeration(r.sourceText);
        if (enumResult.compound || enumResult.complete || !enumResult.unresolved.isEmpty()) {
            return enumResult;
        }

        // Nested: "A and either B or C" / "A and (B or C)"
        ParseResult nested = parseNestedAndOr(r.sourceText);
        if (nested.compound) {
            return nested;
        }

        // Bureau alternative OR list (C3) — before flat AND split on ", NTC and 650"
        ParseResult bureauOr = parseBureauAlternatives(r.sourceText);
        if (bureauOr.compound || bureauOr.complete) {
            return bureauOr;
        }

        // AND of distinct parameters (C4)
        ParseResult anded = parseConjunction(r.sourceText);
        if (anded.compound) {
            return anded;
        }

        // Single comparison (C1/C2)
        ParseResult single = parseSingleComparison(r.sourceText);
        if (single.complete || !single.unresolved.isEmpty()) {
            return single;
        }

        return needsClarification(r, List.of(),
                List.of("Could not map this statement to executable conditions"));
    }

    public static ParseResult amend(Map<String, Object> proposed, String amendmentText) {
        ParseResult r = new ParseResult();
        r.sourceText = amendmentText == null ? "" : amendmentText.trim();
        if (proposed == null) {
            return parse(amendmentText);
        }
        Map<String, Object> model = new LinkedHashMap<>(proposed);
        if (!(model.get("children") instanceof List<?>) && model.get("conditions") instanceof List<?>) {
            model.put("children", model.get("conditions"));
            model.putIfAbsent("kind", CompoundExpressionAuthoringSupport.KIND_GROUP);
        }
        List<Map<String, Object>> children = new ArrayList<>(
                CompoundExpressionAuthoringSupport.childrenOf(model));
        String combinator = String.valueOf(model.getOrDefault("combinator",
                CompoundExpressionAuthoringSupport.COMBINATOR_ANY));
        String lower = r.sourceText.toLowerCase(Locale.ROOT);

        if (isAdversarialVague(lower)) {
            r.children = children;
            r.conditions = CompoundExpressionAuthoringSupport.childrenOf(
                    Map.of("children", children, "combinator", combinator));
            return needsClarification(r, understoodFromChildren(children),
                    List.of(r.sourceText + " — not a measurable amendment"));
        }

        if (lower.contains("also allow") || lower.contains("also add") || lower.startsWith("also ")
                || lower.contains("i also want") || lower.contains("want to add")) {
            String frag = stripAlsoPrefix(r.sourceText);
            ParseResult add = parse(frag);
            if (!add.complete && add.children.isEmpty() && add.conditions.isEmpty()) {
                // Try bureau fragments: NTC / score -1 / threshold
                add = parseBureauFragment(frag);
            }
            if (!add.unresolved.isEmpty() && add.children.isEmpty() && add.conditions.isEmpty()) {
                r.children = children;
                return needsClarification(r, understoodFromChildren(children), add.unresolved);
            }
            List<Map<String, Object>> toAdd = !add.children.isEmpty() ? add.children : add.conditions;
            for (Map<String, Object> c : toAdd) {
                if (children.stream().noneMatch(x -> sameLeaf(x, c))) children.add(ensureCondition(c));
            }
            combinator = CompoundExpressionAuthoringSupport.COMBINATOR_ANY;
        } else if (lower.contains("remove ") || lower.startsWith("except ")) {
            if (lower.contains("ntc")) {
                children.removeIf(c -> CompoundExpressionAuthoringSupport.NTC_FACT
                        .equals(leafParam(c)));
            }
            Matcher m = Pattern.compile("(-?\\d+)").matcher(lower);
            while (m.find()) {
                Object num = parseNum(m.group(1));
                children.removeIf(c -> sameScoreValue(c, num));
            }
        } else if (lower.matches(".*\\bchange\\s+(-?\\d+(?:\\.\\d+)?)\\s+to\\s+(-?\\d+(?:\\.\\d+)?)\\b.*")) {
            Matcher m = Pattern.compile("\\bchange\\s+(-?\\d+(?:\\.\\d+)?)\\s+to\\s+(-?\\d+(?:\\.\\d+)?)\\b",
                    Pattern.CASE_INSENSITIVE).matcher(lower);
            if (m.find()) {
                Object from = parseNum(m.group(1));
                Object to = parseNum(m.group(2));
                for (Map<String, Object> c : children) {
                    if (sameScoreValue(c, from)) c.put("value", to);
                    rewriteNestedValues(c, from, to);
                }
            }
        } else if (lower.contains("make it greater than") || lower.contains("make it more than")
                || lower.contains("make it above")) {
            Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(lower);
            Object n = m.find() ? parseNum(m.group(1)) : null;
            for (Map<String, Object> c : children) {
                if (CompoundExpressionAuthoringSupport.BUREAU_SCORE.equals(leafParam(c))) {
                    c.put("operator", ">");
                    if (n != null) c.put("value", n);
                }
            }
        } else if (lower.contains("make it") && (lower.contains("and above") || lower.contains("& above")
                || lower.contains("at least") || lower.contains("or above"))) {
            Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(lower);
            Object n = m.find() ? parseNum(m.group(1)) : null;
            for (Map<String, Object> c : children) {
                if (CompoundExpressionAuthoringSupport.BUREAU_SCORE.equals(leafParam(c))) {
                    c.put("operator", ">=");
                    if (n != null) c.put("value", n);
                }
            }
        } else if (lower.contains("instead")) {
            return parse(r.sourceText.replaceFirst("(?i).*\\binstead\\b", "").trim());
        } else {
            ParseResult neu = parse(r.sourceText);
            if (neu.complete) return neu;
            ParseResult add = parseBureauFragment(r.sourceText);
            for (Map<String, Object> c : add.conditions) {
                if (children.stream().noneMatch(x -> sameLeaf(x, c))) children.add(c);
            }
            r.unresolved.addAll(add.unresolved);
        }

        return finalizeGroup(r, combinator, children);
    }

    public static boolean looksLikeMultiClause(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (looksLikeIfBranchWording(lower)) return true;
        // Vague / adversarial NL must fail closed via compound parse (never flat DESCRIBE)
        if (isAdversarialVague(lower)) return true;
        if (lower.contains(" either ") || lower.contains(" or below") && lower.contains(" and ")) return true;
        if (lower.contains(" must be ") && lower.contains(" and ")
                && (lower.contains("foir") || lower.contains("ltv") || lower.contains("bureau"))) {
            return true;
        }
        if ((lower.contains("must be") || lower.contains("must not"))
                && (lower.contains(",") || lower.contains(" or "))
                && (lower.contains("propriet") || lower.contains("partner") || lower.contains("industry")
                || lower.contains("gambling") || lower.contains("real estate"))) {
            return true;
        }
        boolean bureau = lower.contains("bureau") || lower.contains("cibil") || lower.contains("score");
        if (bureau && (lower.matches(".*\\bntc\\b.*") || SIGNED_NUMBER.matcher(text).find())
                && (lower.contains("above") || lower.contains("at least") || lower.contains("or more")
                || lower.contains("or above"))) {
            return true;
        }
        return false;
    }

    public static String detectBoundaryOperator(String phrase) {
        if (phrase == null) return null;
        String lower = phrase.toLowerCase(Locale.ROOT);
        if (INCLUSIVE_ABOVE.matcher(lower).find() || INCLUSIVE_OR_MORE.matcher(lower).find()
                || AT_LEAST.matcher(lower).find()
                || lower.contains("and above") || lower.contains("& above")
                || lower.contains("or more") || lower.contains("or higher")
                || lower.contains("or above")) {
            return ">=";
        }
        if (INCLUSIVE_BELOW.matcher(lower).find() || lower.contains("and below")
                || lower.contains("& below") || lower.contains("not exceed")
                || lower.contains("at most") || lower.contains("or below")) {
            return "<=";
        }
        if (EXCLUSIVE_ABOVE.matcher(lower).find()) return ">";
        if (EXCLUSIVE_BELOW.matcher(lower).find()) return "<";
        return null;
    }

    // ─── specialised parsers ─────────────────────────────────────────────

    private static ParseResult parseBureauAlternatives(String text) {
        ParseResult r = new ParseResult();
        r.sourceText = text;
        String lower = text.toLowerCase(Locale.ROOT);
        boolean bureauCtx = lower.contains("bureau") || lower.contains("cibil")
                || lower.contains("credit score") || lower.contains("bureau score");
        if (!bureauCtx) return r;

        boolean altList = (lower.contains("only will be allowed") || lower.contains("will be allowed")
                || lower.contains("only allowed") || lower.contains(",")
                || lower.contains(" and "))
                && (SIGNED_NUMBER.matcher(text).find() || lower.matches(".*\\bntc\\b.*"));

        List<Map<String, Object>> conditions = new ArrayList<>();
        List<String> understood = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        if (!(altList || lower.matches(".*\\bntc\\b.*") || SIGNED_NUMBER.matcher(text).find())) {
            return r;
        }

        Matcher neg = SIGNED_NUMBER.matcher(text);
        while (neg.find()) {
            long v = Long.parseLong(neg.group(1));
            understood.add("Bureau score = " + v);
            conditions.add(scoreCond("=", v));
        }
        if (lower.matches(".*\\bntc\\b.*") || lower.contains("new to credit")
                || lower.contains("thin file") || lower.contains("thin-file")) {
            understood.add("NTC");
            conditions.add(ntcCond());
        }
        String residual = text;
        residual = consumeThreshold(residual, INCLUSIVE_ABOVE, ">=", conditions, understood);
        residual = consumeThreshold(residual, INCLUSIVE_OR_MORE, ">=", conditions, understood);
        residual = consumeThreshold(residual, AT_LEAST, ">=", conditions, understood);
        residual = consumeThreshold(residual, INCLUSIVE_BELOW, "<=", conditions, understood);
        residual = consumeThreshold(residual, EXCLUSIVE_ABOVE, ">", conditions, understood);
        residual = consumeThreshold(residual, EXCLUSIVE_BELOW, "<", conditions, understood);

        if (conditions.size() <= 1 && !altList) {
            return r; // let single comparison handle
        }
        r.compound = true;
        r.combinator = CompoundExpressionAuthoringSupport.COMBINATOR_ANY;
        r.understood = understood;
        r.extractedClauses = new ArrayList<>(understood);
        r.unresolved = unresolved;
        return finalizeGroup(r, r.combinator, conditions);
    }

    private static ParseResult parseBureauFragment(String frag) {
        ParseResult r = new ParseResult();
        r.sourceText = frag;
        String lower = frag.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> conditions = new ArrayList<>();
        List<String> understood = new ArrayList<>();
        if (lower.matches(".*\\bntc\\b.*")) {
            conditions.add(ntcCond());
            understood.add("NTC");
        }
        Matcher neg = SIGNED_NUMBER.matcher(frag);
        while (neg.find()) {
            conditions.add(scoreCond("=", Long.parseLong(neg.group(1))));
            understood.add("Bureau score = " + neg.group(1));
        }
        consumeThreshold(frag, INCLUSIVE_ABOVE, ">=", conditions, understood);
        consumeThreshold(frag, INCLUSIVE_OR_MORE, ">=", conditions, understood);
        consumeThreshold(frag, AT_LEAST, ">=", conditions, understood);
        if (conditions.isEmpty() && lower.contains("score") && lower.contains("-1")) {
            conditions.add(scoreCond("=", -1L));
        }
        r.compound = conditions.size() > 1;
        r.understood = understood;
        return finalizeGroup(r, CompoundExpressionAuthoringSupport.COMBINATOR_ANY, conditions);
    }

    private static ParseResult parseConjunction(String text) {
        ParseResult r = new ParseResult();
        r.sourceText = text;
        String lower = text.toLowerCase(Locale.ROOT);
        // Split on " and " when both sides look like different parameter clauses
        if (!lower.contains(" and ") || lower.contains(" either ")) return r;
        String[] parts = text.split("(?i)\\band\\b");
        if (parts.length < 2) return r;
        List<Map<String, Object>> children = new ArrayList<>();
        List<String> understood = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String part : parts) {
            ParseResult one = parseSingleComparison(part.trim());
            if (one.complete && !one.children.isEmpty()) {
                children.addAll(one.children);
                understood.addAll(one.understood);
            } else if (one.complete && !one.conditions.isEmpty()) {
                children.addAll(one.conditions);
                understood.addAll(one.understood);
            } else if (!one.unresolved.isEmpty()) {
                unresolved.addAll(one.unresolved);
            } else {
                // try bureau fragment
                ParseResult b = parseBureauFragment(part.trim());
                if (!b.conditions.isEmpty()) {
                    children.addAll(b.conditions);
                    understood.addAll(b.understood);
                } else {
                    unresolved.add(part.trim());
                }
            }
        }
        if (children.size() < 2 && unresolved.isEmpty()) return r;
        r.compound = true;
        r.understood = understood;
        r.unresolved = unresolved;
        if (!unresolved.isEmpty()) {
            r.children = children;
            return needsClarification(r, understood, unresolved);
        }
        return finalizeGroup(r, CompoundExpressionAuthoringSupport.COMBINATOR_ALL, children);
    }

    private static ParseResult parseNestedAndOr(String text) {
        ParseResult r = new ParseResult();
        r.sourceText = text;
        String lower = text.toLowerCase(Locale.ROOT);
        // Pattern: X and either Y or Z — right clause must start with a parameter token
        // so "50% or below" is not treated as the OR split.
        Matcher m = Pattern.compile(
                "(?i)^(.+?)\\s+and\\s+either\\s+(.+)\\s+or\\s+"
                        + "((?:foir|ltv|bureau|loan(?:\\s*-?\\s*to\\s*-?\\s*value)?|borrower|industry|"
                        + "constitution|score|cibil|obligation|collateral)\\b.+)$")
                .matcher(text.trim());
        if (!m.matches()) {
            return r;
        }
        ParseResult left = parseSingleComparison(m.group(1).trim());
        ParseResult a = parseSingleComparison(m.group(2).trim());
        ParseResult b = parseSingleComparison(m.group(3).trim());
        List<String> understood = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<Map<String, Object>> leftChildren = leavesOf(left);
        List<Map<String, Object>> aChildren = leavesOf(a);
        List<Map<String, Object>> bChildren = leavesOf(b);
        if (leftChildren.isEmpty()) unresolved.add(m.group(1).trim());
        else understood.addAll(left.understood);
        if (aChildren.isEmpty()) unresolved.add(m.group(2).trim());
        else understood.addAll(a.understood);
        if (bChildren.isEmpty()) unresolved.add(m.group(3).trim());
        else understood.addAll(b.understood);

        // Unmapped domain terms (e.g. LTV missing from catalogue) already in unresolved
        unresolved.addAll(left.unresolved);
        unresolved.addAll(a.unresolved);
        unresolved.addAll(b.unresolved);

        Map<String, Object> orGroup = new LinkedHashMap<>();
        orGroup.put("kind", CompoundExpressionAuthoringSupport.KIND_GROUP);
        orGroup.put("combinator", CompoundExpressionAuthoringSupport.COMBINATOR_ANY);
        List<Map<String, Object>> orChildren = new ArrayList<>();
        orChildren.addAll(aChildren);
        orChildren.addAll(bChildren);
        orGroup.put("children", orChildren);

        List<Map<String, Object>> rootChildren = new ArrayList<>(leftChildren);
        rootChildren.add(orGroup);

        r.compound = true;
        r.understood = understood;
        r.unresolved = unresolved;
        if (!unresolved.isEmpty()) {
            r.children = rootChildren;
            return needsClarification(r, understood, unresolved);
        }
        return finalizeGroup(r, CompoundExpressionAuthoringSupport.COMBINATOR_ALL, rootChildren);
    }

    private static ParseResult parseEnumeration(String text) {
        ParseResult r = new ParseResult();
        r.sourceText = text;
        String lower = text.toLowerCase(Locale.ROOT);

        boolean constitution = lower.contains("constitution") || lower.contains("borrower type")
                || lower.contains("propriet") || lower.contains("partnership")
                || (lower.contains("llp") && (lower.contains("must be") || lower.contains("must not")));
        boolean industry = lower.contains("industry");

        if (!constitution && !industry) return r;

        boolean notIn = lower.contains("must not") || lower.contains("not be")
                || lower.contains("cannot be") || lower.contains("other than");
        List<String> tokens = extractEnumTokens(text);
        if (tokens.isEmpty()) return r;

        if (constitution) {
            List<Object> mapped = new ArrayList<>();
            List<String> unresolved = new ArrayList<>();
            List<String> understood = new ArrayList<>();
            for (String t : tokens) {
                String code = mapBorrowerType(t);
                if (code == null) {
                    unresolved.add(t + " (no canonical borrower type)");
                } else {
                    mapped.add(code);
                    understood.add(code);
                }
            }
            r.compound = true;
            if (!unresolved.isEmpty()) {
                r.understood = understood;
                return needsClarification(r, understood, unresolved);
            }
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("kind", CompoundExpressionAuthoringSupport.KIND_CONDITION);
            cond.put("parameterId", CompoundExpressionAuthoringSupport.BORROWER_TYPE);
            cond.put("parameterName", "Borrower type");
            cond.put("operator", notIn ? "not in" : "in");
            cond.put("values", mapped);
            cond.put("value", mapped);
            cond.put("leftKind", "METRIC");
            cond.put("valueControl", AuthoringValueTypes.CONTROL_ENUM);
            r.understood = understood;
            return finalizeGroup(r, CompoundExpressionAuthoringSupport.COMBINATOR_ALL, List.of(cond));
        }

        if (industry) {
            List<Object> mapped = new ArrayList<>();
            List<String> understood = new ArrayList<>();
            for (String t : tokens) {
                String code = mapIndustry(t);
                mapped.add(code);
                understood.add(code);
            }
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("kind", CompoundExpressionAuthoringSupport.KIND_CONDITION);
            cond.put("parameterId", CompoundExpressionAuthoringSupport.INDUSTRY);
            cond.put("parameterName", "Industry type");
            cond.put("operator", notIn ? "not in" : "in");
            cond.put("values", mapped);
            cond.put("value", mapped);
            cond.put("leftKind", "METRIC");
            cond.put("valueControl", AuthoringValueTypes.CONTROL_STRING);
            r.compound = true;
            r.understood = understood;
            return finalizeGroup(r, CompoundExpressionAuthoringSupport.COMBINATOR_ALL, List.of(cond));
        }
        return r;
    }

    private static ParseResult parseSingleComparison(String text) {
        ParseResult r = new ParseResult();
        r.sourceText = text;
        String lower = text.toLowerCase(Locale.ROOT);
        String paramId = detectParameter(lower);
        if (paramId == null) {
            if (hasConcreteThreshold(lower) && (lower.contains("score") || lower.contains("bureau"))) {
                paramId = CompoundExpressionAuthoringSupport.BUREAU_SCORE;
            } else {
                return r;
            }
        }
        // LTV: authoring overlay — known catalogue metric path
        if ("ltv".equals(paramId) || lower.contains("ltv")) {
            paramId = CompoundExpressionAuthoringSupport.LTV;
        }

        String op = detectBoundaryOperator(lower);
        Object value = extractThresholdValue(text);
        if (op == null && value != null && (lower.contains("must be") || lower.contains("should be")
                || lower.contains(" is "))) {
            op = "=";
        }
        if (op == null || value == null) {
            if (paramId != null && (lower.contains("good") || lower.contains("high") || lower.contains("low"))) {
                return needsClarification(r, List.of(),
                        List.of(text.trim() + " — missing measurable threshold"));
            }
            return r;
        }
        if (!CompoundExpressionAuthoringSupport.operatorAllowed(paramId, op)) {
            return needsClarification(r, List.of(),
                    List.of(op + " is not valid for " + CompoundExpressionAuthoringSupport.friendlyParam(paramId)));
        }
        Map<String, Object> cond = new LinkedHashMap<>();
        cond.put("kind", CompoundExpressionAuthoringSupport.KIND_CONDITION);
        cond.put("parameterId", paramId);
        cond.put("parameterName", CompoundExpressionAuthoringSupport.friendlyParam(paramId));
        cond.put("operator", op);
        cond.put("value", value);
        cond.put("leftKind", CompoundExpressionAuthoringSupport.NTC_FACT.equals(paramId) ? "FACT" : "METRIC");
        cond.put("valueControl", CompoundExpressionAuthoringSupport.valueControlFor(paramId));
        r.understood = List.of(CompoundExpressionAuthoringSupport.conditionDisplay(cond));
        // Single comparison is complete even as one-child ALL group
        r.compound = false;
        return finalizeGroup(r, CompoundExpressionAuthoringSupport.COMBINATOR_ALL, List.of(cond));
    }

    private static String detectParameter(String lower) {
        if (lower.contains("bureau score") || lower.contains("cibil")
                || (lower.contains("score") && (lower.contains("bureau") || lower.contains("credit")))) {
            return CompoundExpressionAuthoringSupport.BUREAU_SCORE;
        }
        if (lower.contains("foir") || lower.contains("obligation ratio") || lower.contains("dti")) {
            return CompoundExpressionAuthoringSupport.FOIR;
        }
        if (lower.contains("ltv") || lower.contains("loan to value") || lower.contains("loan-to-value")) {
            return CompoundExpressionAuthoringSupport.LTV;
        }
        if (lower.contains("borrower type") || lower.contains("constitution")) {
            return CompoundExpressionAuthoringSupport.BORROWER_TYPE;
        }
        if (lower.contains("industry")) {
            return CompoundExpressionAuthoringSupport.INDUSTRY;
        }
        if (lower.matches(".*\\bntc\\b.*")) {
            return CompoundExpressionAuthoringSupport.NTC_FACT;
        }
        return null;
    }

    private static Object extractThresholdValue(String text) {
        for (Pattern p : List.of(INCLUSIVE_ABOVE, INCLUSIVE_OR_MORE, AT_LEAST, EXCLUSIVE_ABOVE,
                EXCLUSIVE_BELOW, INCLUSIVE_BELOW)) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                for (int g = 1; g <= m.groupCount(); g++) {
                    if (m.group(g) != null) return parseNum(m.group(g));
                }
            }
        }
        Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*%?").matcher(text);
        Double last = null;
        while (m.find()) {
            last = Double.parseDouble(m.group(1));
        }
        if (last == null) return null;
        if (last == Math.rint(last)) return last.longValue();
        return last;
    }

    private static List<String> extractEnumTokens(String text) {
        String cleaned = text.replaceAll("(?i)\\b(borrower\\s+type|constitution|industry|must\\s+be|must\\s+not\\s+be|must\\s+not|should\\s+be|can\\s+be|only)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(proprietorship|proprietor|partnership|llp|company|individual|real\\s+estate|gambling|crypto|cryptocurrency)\\b",
                "::$0::");
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("::([^:]+)::").matcher(cleaned);
        while (m.find()) out.add(m.group(1).trim());
        if (out.isEmpty()) {
            for (String part : text.split("(?i),|\\bor\\b|\\band\\b")) {
                String t = part.trim();
                if (t.length() > 2 && !t.equalsIgnoreCase("must") && !t.equalsIgnoreCase("be")) {
                    // skip
                }
            }
        }
        return out;
    }

    private static String mapBorrowerType(String token) {
        String t = token.toLowerCase(Locale.ROOT).trim();
        if (t.contains("propriet")) return "PROPRIETOR";
        if (t.contains("partner")) return "PARTNERSHIP";
        if (t.equals("llp") || t.contains("limited liability")) return null; // not in BorrowerType enum
        if (t.contains("compan") || t.contains("private limited") || t.contains("pvt")) return "COMPANY";
        if (t.contains("individual")) return "INDIVIDUAL";
        return null;
    }

    private static String mapIndustry(String token) {
        String t = token.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (t.contains("real")) return "REAL_ESTATE";
        if (t.contains("gambl")) return "GAMBLING";
        if (t.contains("crypto")) return "CRYPTO";
        return t.toUpperCase(Locale.ROOT);
    }

    // ─── finalize / helpers ──────────────────────────────────────────────

    private static ParseResult finalizeGroup(ParseResult r, String combinator, List<Map<String, Object>> children) {
        if (children == null || children.isEmpty()) {
            if (!r.unresolved.isEmpty()) {
                return needsClarification(r, r.understood, r.unresolved);
            }
            return incomplete(r, "No conditions mapped");
        }
        r.combinator = combinator;
        r.children = children;
        r.conditions = flattenForCompat(children);
        // Single IN/NOT IN / FACT leaf must still route through compound preview (not flat DESCRIBE)
        r.compound = children.size() > 1
                || children.stream().anyMatch(c ->
                CompoundExpressionAuthoringSupport.KIND_GROUP.equalsIgnoreCase(String.valueOf(c.get("kind")))
                        || "in".equalsIgnoreCase(String.valueOf(c.get("operator")))
                        || "not in".equalsIgnoreCase(String.valueOf(c.get("operator")))
                        || CompoundExpressionAuthoringSupport.NTC_FACT.equals(c.get("parameterId")));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("kind", CompoundExpressionAuthoringSupport.KIND_GROUP);
        model.put("combinator", combinator);
        model.put("children", children);
        if (!r.unresolved.isEmpty()) {
            r.editableModel = model;
            return needsClarification(r, r.understood.isEmpty() ? understoodFromChildren(children) : r.understood,
                    r.unresolved);
        }
        try {
            r.expression = CompoundExpressionAuthoringSupport.buildExpression(model);
        } catch (IllegalArgumentException ex) {
            return incomplete(r, ex.getMessage());
        }
        r.editableModel = CompoundExpressionAuthoringSupport.toEditableModel(r.expression, Map.of());
        r.previewLines = CompoundExpressionAuthoringSupport.previewLines(model);
        r.ruleDisplay = CompoundExpressionAuthoringSupport.businessSummary(model);
        r.complete = true;
        r.status = "READY";
        r.message = "Ready to confirm";
        r.extractedClauses = r.understood;
        return r;
    }

    private static ParseResult needsClarification(ParseResult r, List<String> understood, List<String> clarify) {
        r.complete = false;
        r.status = "NEEDS_CLARIFICATION";
        r.understood = understood == null ? List.of() : understood;
        r.unresolved = clarify == null ? List.of() : clarify;
        r.message = "Some parts of this rule have not been mapped yet.";
        r.compound = true;
        return r;
    }

    private static ParseResult incomplete(ParseResult r, String msg) {
        r.complete = false;
        r.status = "INCOMPLETE";
        r.message = msg;
        return r;
    }

    private static boolean isAdversarialVague(String lower) {
        if (!VAGUE.matcher(lower).find()) return false;
        if (lower.contains("or something") || lower.contains("maybe") || lower.contains("around")) {
            return true;
        }
        // Vague adjective without a concrete measurable boundary
        return !hasConcreteThreshold(lower) || detectBoundaryOperator(lower) == null;
    }

    private static boolean hasConcreteThreshold(String lower) {
        return Pattern.compile("\\d").matcher(lower).find()
                || lower.matches(".*\\bntc\\b.*");
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

    private static String consumeThreshold(
            String text, Pattern p, String op,
            List<Map<String, Object>> conditions, List<String> understood) {
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String g = null;
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) { g = m.group(i); break; }
            }
            if (g == null) continue;
            Object num = parseNum(g);
            understood.add("Bureau score " + op + " " + formatNum(num));
            if (conditions.stream().noneMatch(c -> sameScoreValue(c, num) && op.equals(c.get("operator")))) {
                conditions.add(scoreCond(op, num));
            }
            m.appendReplacement(sb, " ");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, Object> scoreCond(String op, Object value) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("kind", CompoundExpressionAuthoringSupport.KIND_CONDITION);
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
        c.put("kind", CompoundExpressionAuthoringSupport.KIND_CONDITION);
        c.put("parameterId", CompoundExpressionAuthoringSupport.NTC_FACT);
        c.put("parameterName", "Bureau status");
        c.put("operator", "is");
        c.put("value", true);
        c.put("leftKind", "FACT");
        c.put("valueControl", AuthoringValueTypes.CONTROL_BOOLEAN);
        c.put("valueLabel", "NTC");
        return c;
    }

    private static Map<String, Object> ensureCondition(Map<String, Object> c) {
        Map<String, Object> copy = new LinkedHashMap<>(c);
        copy.putIfAbsent("kind", CompoundExpressionAuthoringSupport.KIND_CONDITION);
        return copy;
    }

    private static List<Map<String, Object>> leavesOf(ParseResult p) {
        if (p == null) return List.of();
        if (!p.children.isEmpty()) return p.children;
        return p.conditions;
    }

    private static List<Map<String, Object>> flattenForCompat(List<Map<String, Object>> children) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : children) {
            if (CompoundExpressionAuthoringSupport.KIND_GROUP.equalsIgnoreCase(String.valueOf(c.get("kind")))) {
                out.addAll(CompoundExpressionAuthoringSupport.childrenOf(c));
            } else {
                out.add(c);
            }
        }
        return out;
    }

    private static List<String> understoodFromChildren(List<Map<String, Object>> children) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> c : children) {
            if (CompoundExpressionAuthoringSupport.KIND_GROUP.equalsIgnoreCase(String.valueOf(c.get("kind")))) {
                out.add("(" + CompoundExpressionAuthoringSupport.businessSummary(c) + ")");
            } else {
                out.add(CompoundExpressionAuthoringSupport.conditionDisplay(c));
            }
        }
        return out;
    }

    private static boolean sameLeaf(Map<String, Object> a, Map<String, Object> b) {
        return ObjectsEquals(leafParam(a), leafParam(b))
                && ObjectsEquals(String.valueOf(a.get("operator")), String.valueOf(b.get("operator")))
                && ObjectsEquals(a.get("value"), b.get("value"));
    }

    private static String leafParam(Map<String, Object> c) {
        return c == null ? null : String.valueOf(c.get("parameterId"));
    }

    private static boolean sameScoreValue(Map<String, Object> c, Object n) {
        return CompoundExpressionAuthoringSupport.BUREAU_SCORE.equals(leafParam(c))
                && ObjectsEquals(c.get("value"), n);
    }

    private static void rewriteNestedValues(Map<String, Object> node, Object from, Object to) {
        if (CompoundExpressionAuthoringSupport.KIND_GROUP.equalsIgnoreCase(String.valueOf(node.get("kind")))) {
            for (Map<String, Object> c : CompoundExpressionAuthoringSupport.childrenOf(node)) {
                if (sameScoreValue(c, from)) c.put("value", to);
                rewriteNestedValues(c, from, to);
            }
        }
    }

    private static boolean ObjectsEquals(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        return java.util.Objects.equals(a, b);
    }

    private static String stripAlsoPrefix(String text) {
        return text.replaceFirst(
                "(?i)^\\s*(i\\s+also\\s+want\\s+to\\s+add|also\\s+allow|also\\s+add|also|add)\\s+", "")
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
}
