package com.los.core.service.underwriting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * SCORECARD-SAFETY-FOUNDATION-1 — derive mutually exclusive numeric bands from seeded ladder rows.
 * Does not invent boundaries: only unambiguous ordered GTE/GT or LTE/LT ladders (and EQ tokens).
 */
public final class ScorecardExclusiveBandModel {

    public static final String MODE_EXCLUSIVE = "EXCLUSIVE_RANGES";
    public static final String MODE_AMBIGUOUS = "AMBIGUOUS";
    public static final String MODE_NON_NUMERIC = "NON_NUMERIC";

    private ScorecardExclusiveBandModel() {}

    public record ExclusiveBand(
            String rowId,
            String parameter,
            String source,
            String originalCondition,
            BigDecimal lowerInclusive,
            BigDecimal upperExclusive,
            boolean lowerOpen,
            boolean upperOpen,
            boolean eqToken,
            String token,
            int score,
            int weightMetadata) {}

    public record FactorBands(
            String parameter,
            String source,
            String mode,
            List<ExclusiveBand> bands,
            List<String> problems,
            int maxPoints) {}

    public record Model(
            List<FactorBands> factors,
            List<String> problems,
            boolean safe) {}

    @SuppressWarnings("unchecked")
    public static Model fromRows(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String p = str(row.get("parameter"));
            String s = str(row.get("source"));
            if (p == null) continue;
            String key = p + "|" + (s == null ? "" : s);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        List<FactorBands> factors = new ArrayList<>();
        List<String> allProblems = new ArrayList<>();
        for (var e : groups.entrySet()) {
            FactorBands fb = normalizeGroup(e.getValue());
            factors.add(fb);
            allProblems.addAll(fb.problems());
        }
        boolean safe = allProblems.isEmpty()
                && factors.stream().noneMatch(f -> MODE_AMBIGUOUS.equals(f.mode()));
        return new Model(factors, allProblems, safe);
    }

    private static FactorBands normalizeGroup(List<Map<String, Object>> rows) {
        String parameter = str(rows.get(0).get("parameter"));
        String source = str(rows.get(0).get("source"));
        List<String> problems = new ArrayList<>();
        List<Parsed> parsed = new ArrayList<>();
        int unparseable = 0;
        for (Map<String, Object> row : rows) {
            Parsed p = parse(row);
            if (p == null) {
                unparseable++;
                continue;
            }
            parsed.add(p);
        }
        if (parsed.isEmpty()) {
            // MATCH_OPTION / text / other non-ladder rows — scored by legacy single-row path
            return new FactorBands(parameter, source, MODE_NON_NUMERIC, List.of(), List.of(), 0);
        }
        if (unparseable > 0) {
            problems.add(parameter + ": mixed parseable and unparseable conditions");
            return new FactorBands(parameter, source, MODE_AMBIGUOUS, List.of(), problems, 0);
        }
        boolean allEqToken = parsed.stream().allMatch(p -> p.kind == Kind.EQ_TOKEN);
        boolean allGte = parsed.stream().allMatch(p -> p.kind == Kind.GTE || p.kind == Kind.GT);
        boolean allLte = parsed.stream().allMatch(p -> p.kind == Kind.LTE || p.kind == Kind.LT);
        boolean allBetween = parsed.stream().allMatch(p -> p.kind == Kind.BETWEEN);
        boolean allEqNum = parsed.stream().allMatch(p -> p.kind == Kind.EQ_NUM);

        if (allEqToken || allEqNum) {
            return exclusiveEquals(parameter, source, parsed, problems);
        }
        if (allGte) {
            return exclusiveHigherBetter(parameter, source, parsed, problems);
        }
        if (allLte) {
            return exclusiveLowerBetter(parameter, source, parsed, problems);
        }
        if (allBetween) {
            return exclusiveBetweens(parameter, source, parsed, problems);
        }
        if (parsed.size() == 1) {
            return singleBand(parameter, source, parsed.get(0), problems);
        }
        problems.add(parameter + ": mixed condition kinds cannot be auto-exclusived");
        return new FactorBands(parameter, source, MODE_AMBIGUOUS, List.of(), problems, 0);
    }

    private static FactorBands exclusiveHigherBetter(
            String parameter, String source, List<Parsed> parsed, List<String> problems) {
        List<Parsed> sorted = new ArrayList<>(parsed);
        sorted.sort(Comparator.comparing((Parsed p) -> p.a).reversed());
        // duplicate thresholds
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).a.compareTo(sorted.get(i - 1).a) == 0) {
                problems.add(parameter + ": duplicate threshold " + sorted.get(i).a);
            }
        }
        List<ExclusiveBand> bands = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Parsed cur = sorted.get(i);
            BigDecimal upper = i == 0 ? null : sorted.get(i - 1).a; // exclusive upper = next higher threshold
            // For GTE: lower inclusive = a; for GT: lower exclusive conceptually via lowerOpen
            boolean lowerOpen = cur.kind == Kind.GT;
            bands.add(new ExclusiveBand(
                    cur.rowId, parameter, source, cur.original,
                    cur.a, upper, lowerOpen, true, false, null, cur.score, cur.weight));
        }
        int max = bands.stream().mapToInt(ExclusiveBand::score).max().orElse(0);
        return new FactorBands(parameter, source, MODE_EXCLUSIVE, bands, problems, max);
    }

    private static FactorBands exclusiveLowerBetter(
            String parameter, String source, List<Parsed> parsed, List<String> problems) {
        List<Parsed> sorted = new ArrayList<>(parsed);
        sorted.sort(Comparator.comparing(p -> p.a));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).a.compareTo(sorted.get(i - 1).a) == 0) {
                problems.add(parameter + ": duplicate threshold " + sorted.get(i).a);
            }
        }
        List<ExclusiveBand> bands = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Parsed cur = sorted.get(i);
            BigDecimal lower = i == 0 ? null : sorted.get(i - 1).a; // exclusive lower = previous threshold
            boolean upperOpen = cur.kind == Kind.LT;
            // LTE: (-inf, a] or (prev, a]; LT: (-inf, a) or (prev, a)
            bands.add(new ExclusiveBand(
                    cur.rowId, parameter, source, cur.original,
                    lower, cur.a, true, upperOpen, false, null, cur.score, cur.weight));
        }
        int max = bands.stream().mapToInt(ExclusiveBand::score).max().orElse(0);
        return new FactorBands(parameter, source, MODE_EXCLUSIVE, bands, problems, max);
    }

    private static FactorBands exclusiveBetweens(
            String parameter, String source, List<Parsed> parsed, List<String> problems) {
        List<ExclusiveBand> bands = new ArrayList<>();
        List<Parsed> sorted = new ArrayList<>(parsed);
        sorted.sort(Comparator.comparing(p -> p.a));
        for (int i = 0; i < sorted.size(); i++) {
            Parsed cur = sorted.get(i);
            if (cur.a.compareTo(cur.b) > 0) {
                problems.add(parameter + ": BETWEEN lower > upper");
            }
            for (int j = i + 1; j < sorted.size(); j++) {
                Parsed o = sorted.get(j);
                if (overlapsInclusive(cur.a, cur.b, o.a, o.b)) {
                    problems.add(parameter + ": overlapping BETWEEN bands");
                }
            }
            bands.add(new ExclusiveBand(
                    cur.rowId, parameter, source, cur.original,
                    cur.a, cur.b, false, false, false, null, cur.score, cur.weight));
        }
        String mode = problems.isEmpty() ? MODE_EXCLUSIVE : MODE_AMBIGUOUS;
        int max = bands.stream().mapToInt(ExclusiveBand::score).max().orElse(0);
        return new FactorBands(parameter, source, mode, bands, problems, max);
    }

    private static FactorBands exclusiveEquals(
            String parameter, String source, List<Parsed> parsed, List<String> problems) {
        List<ExclusiveBand> bands = new ArrayList<>();
        for (Parsed cur : parsed) {
            for (ExclusiveBand existing : bands) {
                if (Objects.equals(existing.token(), cur.token)
                        || (cur.a != null && existing.lowerInclusive() != null
                        && cur.a.compareTo(existing.lowerInclusive()) == 0 && existing.eqToken())) {
                    problems.add(parameter + ": duplicate EQ band");
                }
            }
            bands.add(new ExclusiveBand(
                    cur.rowId, parameter, source, cur.original,
                    cur.a, cur.a, false, false, true, cur.token, cur.score, cur.weight));
        }
        int max = bands.stream().mapToInt(ExclusiveBand::score).max().orElse(0);
        return new FactorBands(parameter, source, MODE_EXCLUSIVE, bands, problems, max);
    }

    private static FactorBands singleBand(
            String parameter, String source, Parsed cur, List<String> problems) {
        ExclusiveBand band;
        if (cur.kind == Kind.BETWEEN) {
            band = new ExclusiveBand(cur.rowId, parameter, source, cur.original,
                    cur.a, cur.b, false, false, false, null, cur.score, cur.weight);
        } else if (cur.kind == Kind.EQ_TOKEN || cur.kind == Kind.EQ_NUM) {
            band = new ExclusiveBand(cur.rowId, parameter, source, cur.original,
                    cur.a, cur.a, false, false, cur.kind == Kind.EQ_TOKEN, cur.token, cur.score, cur.weight);
        } else if (cur.kind == Kind.GTE || cur.kind == Kind.GT) {
            band = new ExclusiveBand(cur.rowId, parameter, source, cur.original,
                    cur.a, null, cur.kind == Kind.GT, true, false, null, cur.score, cur.weight);
        } else {
            band = new ExclusiveBand(cur.rowId, parameter, source, cur.original,
                    null, cur.a, true, cur.kind == Kind.LT, false, null, cur.score, cur.weight);
        }
        return new FactorBands(parameter, source, MODE_EXCLUSIVE, List.of(band), problems, cur.score);
    }

    public static ExclusiveBand match(FactorBands factor, BigDecimal value, String stringOrToken) {
        if (factor == null || factor.bands() == null) return null;
        for (ExclusiveBand b : factor.bands()) {
            if (b.eqToken()) {
                if (tokenMatches(b.token(), value, stringOrToken)) return b;
                continue;
            }
            if (value == null) continue;
            if (inRange(value, b)) return b;
        }
        return null;
    }

    private static boolean inRange(BigDecimal v, ExclusiveBand b) {
        if (b.lowerInclusive() != null) {
            int cmp = v.compareTo(b.lowerInclusive());
            if (b.lowerOpen() ? cmp <= 0 : cmp < 0) return false;
        }
        if (b.upperExclusive() != null) {
            // For BETWEEN stored with upperOpen=false meaning inclusive upper (LTE style upper bound)
            // Higher-better exclusive uses upperExclusive as exclusive upper (next threshold).
            // Lower-better uses upperExclusive as inclusive/exclusive upper bound via upperOpen.
            int cmp = v.compareTo(b.upperExclusive());
            if (b.upperOpen()) {
                // upperOpen true on higher-better means exclusive upper; on LT means exclusive upper
                if (cmp >= 0) return false;
            } else {
                // inclusive upper (BETWEEN or LTE)
                if (cmp > 0) return false;
            }
        }
        return true;
    }

    private static boolean tokenMatches(String token, BigDecimal v, String stringOrToken) {
        if (token == null) return false;
        String t = token.trim().toUpperCase(Locale.ROOT);
        if (stringOrToken != null && t.equals(stringOrToken.trim().toUpperCase(Locale.ROOT))) return true;
        if (v == null) return false;
        // PASS/CLEAN/LOW coded as 1
        if (SetOfPass.contains(t)) return v.compareTo(BigDecimal.ONE) == 0;
        if ("FAIL".equals(t) || "0".equals(t)) return v.compareTo(BigDecimal.ZERO) == 0;
        try {
            return v.compareTo(new BigDecimal(t)) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static final java.util.Set<String> SetOfPass = java.util.Set.of("PASS", "CLEAN", "LOW", "1", "TRUE", "YES");

    private static boolean overlapsInclusive(BigDecimal a1, BigDecimal b1, BigDecimal a2, BigDecimal b2) {
        return a1.compareTo(b2) <= 0 && a2.compareTo(b1) <= 0;
    }

    private enum Kind { GTE, GT, LTE, LT, BETWEEN, EQ_TOKEN, EQ_NUM }

    private static final class Parsed {
        String rowId;
        String original;
        Kind kind;
        BigDecimal a;
        BigDecimal b;
        String token;
        int score;
        int weight;
    }

    private static Parsed parse(Map<String, Object> row) {
        String cond = str(row.get("condition"));
        if (cond == null || cond.isBlank()) return null;
        Parsed p = new Parsed();
        p.rowId = str(row.get("id"));
        p.original = cond.trim();
        p.score = intOr(row.get("score"), 0);
        p.weight = intOr(row.get("weight"), 1);
        String c = p.original;
        int colon = c.indexOf(':');
        if (colon < 0) return null;
        String op = c.substring(0, colon).trim().toUpperCase(Locale.ROOT);
        String rest = c.substring(colon + 1).trim();
        try {
            switch (op) {
                case "GTE" -> { p.kind = Kind.GTE; p.a = new BigDecimal(rest); }
                case "GT" -> { p.kind = Kind.GT; p.a = new BigDecimal(rest); }
                case "LTE" -> { p.kind = Kind.LTE; p.a = new BigDecimal(rest); }
                case "LT" -> { p.kind = Kind.LT; p.a = new BigDecimal(rest); }
                case "BETWEEN" -> {
                    int mid = rest.indexOf(':');
                    if (mid < 0) return null;
                    p.kind = Kind.BETWEEN;
                    p.a = new BigDecimal(rest.substring(0, mid).trim());
                    p.b = new BigDecimal(rest.substring(mid + 1).trim());
                }
                case "EQ", "NE" -> {
                    if (op.equals("NE")) return null; // hard-rule style; not a scoring band ladder
                    if (isNumeric(rest)) {
                        p.kind = Kind.EQ_NUM;
                        p.a = new BigDecimal(rest);
                        p.token = rest;
                    } else {
                        p.kind = Kind.EQ_TOKEN;
                        p.token = rest.toUpperCase(Locale.ROOT);
                    }
                }
                default -> { return null; }
            }
        } catch (Exception e) {
            return null;
        }
        return p;
    }

    private static boolean isNumeric(String s) {
        try {
            new BigDecimal(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static int intOr(Object o, int d) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return d;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return d;
        }
    }
}
