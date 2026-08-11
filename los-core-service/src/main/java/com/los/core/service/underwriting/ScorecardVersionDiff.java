package com.los.core.service.underwriting;

import com.los.core.model.entity.UnderwritingScorecard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Scorecard-domain diff for checker review — not a generic document diff. */
public final class ScorecardVersionDiff {

    private ScorecardVersionDiff() {}

    public static Map<String, Object> diff(UnderwritingScorecard previous, UnderwritingScorecard current) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (previous == null) {
            out.put("hasPrevious", false);
            out.put("changes", List.of());
            return out;
        }
        out.put("hasPrevious", true);
        out.put("previousId", previous.getId() == null ? null : previous.getId().toString());
        out.put("previousVersion", previous.getVersion());
        out.put("currentVersion", current.getVersion());
        List<Map<String, Object>> changes = new ArrayList<>();

        compareThreshold(changes, previous.getThresholdsJson(), current.getThresholdsJson(), "approveMinPercent");
        compareThreshold(changes, previous.getThresholdsJson(), current.getThresholdsJson(), "manualMinPercent");

        Map<String, List<Map<String, Object>>> prevBands = bandsByParam(previous);
        Map<String, List<Map<String, Object>>> currBands = bandsByParam(current);
        Set<String> params = new LinkedHashSet<>();
        params.addAll(prevBands.keySet());
        params.addAll(currBands.keySet());
        for (String p : params) {
            if (!prevBands.containsKey(p)) {
                changes.add(change("FACTOR_ADDED", p, null, summarizeBands(currBands.get(p))));
                continue;
            }
            if (!currBands.containsKey(p)) {
                changes.add(change("FACTOR_REMOVED", p, summarizeBands(prevBands.get(p)), null));
                continue;
            }
            Map<String, Integer> prevPts = pointsByCondition(prevBands.get(p));
            Map<String, Integer> currPts = pointsByCondition(currBands.get(p));
            Set<String> conds = new LinkedHashSet<>();
            conds.addAll(prevPts.keySet());
            conds.addAll(currPts.keySet());
            for (String c : conds) {
                Integer a = prevPts.get(c);
                Integer b = currPts.get(c);
                if (!Objects.equals(a, b)) {
                    changes.add(change("BAND_POINTS", p + " / " + c,
                            a == null ? null : a.toString(), b == null ? null : b.toString()));
                }
            }
            String prevMiss = missingPolicy(previous, p);
            String currMiss = missingPolicy(current, p);
            if (!Objects.equals(prevMiss, currMiss)) {
                changes.add(change("MISSING_DATA_POLICY", p, prevMiss, currMiss));
            }
        }
        out.put("changes", changes);
        out.put("changeCount", changes.size());
        return out;
    }

    private static void compareThreshold(
            List<Map<String, Object>> changes,
            Map<String, Object> prev,
            Map<String, Object> curr,
            String key) {
        Object a = prev == null ? null : prev.get(key);
        Object b = curr == null ? null : curr.get(key);
        if (!Objects.equals(String.valueOf(a), String.valueOf(b))) {
            changes.add(change("THRESHOLD", key, a == null ? null : String.valueOf(a),
                    b == null ? null : String.valueOf(b)));
        }
    }

    private static Map<String, Object> change(String type, String subject, String from, String to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("subject", subject);
        m.put("from", from);
        m.put("to", to);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> bandsByParam(UnderwritingScorecard card) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        Object rows = card.getScorecardJson() == null ? null : card.getScorecardJson().get("rows");
        if (!(rows instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> rm)) continue;
            Map<String, Object> row = (Map<String, Object>) rm;
            String p = row.get("parameter") == null ? null : String.valueOf(row.get("parameter"));
            if (p == null || p.isBlank()) continue;
            out.computeIfAbsent(p, k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    private static Map<String, Integer> pointsByCondition(List<Map<String, Object>> rows) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String c = r.get("condition") == null ? "" : String.valueOf(r.get("condition"));
            int score = r.get("score") instanceof Number n ? n.intValue() : 0;
            out.put(c, score);
        }
        return out;
    }

    private static String summarizeBands(List<Map<String, Object>> rows) {
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            parts.add(String.valueOf(r.get("condition")) + "=" + String.valueOf(r.get("score")));
        }
        return String.join("; ", parts);
    }

    @SuppressWarnings("unchecked")
    private static String missingPolicy(UnderwritingScorecard card, String param) {
        Map<String, Object> safety = card.getSafetyJson() == null ? Map.of() : card.getSafetyJson();
        Object fp = safety.get("factorPolicies");
        if (fp instanceof Map<?, ?> m && m.get(param) instanceof Map<?, ?> pol) {
            Object md = pol.get("missingData");
            return md == null ? null : String.valueOf(md);
        }
        // row-level missingData
        Object rows = card.getScorecardJson() == null ? null : card.getScorecardJson().get("rows");
        if (rows instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> rm && param.equals(String.valueOf(rm.get("parameter")))) {
                    Object md = rm.get("missingData");
                    if (md != null) return String.valueOf(md);
                }
            }
        }
        return null;
    }
}
