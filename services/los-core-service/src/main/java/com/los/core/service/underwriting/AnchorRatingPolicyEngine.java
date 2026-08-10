package com.los.core.service.underwriting;

import com.los.core.model.entity.AnchorRatingTemplate;
import com.los.core.repository.AnchorRatingTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves anchor due-diligence questions and scores from the active DB template,
 * with a built-in default matching the original hardcoded checklist.
 */
@Service
@RequiredArgsConstructor
public class AnchorRatingPolicyEngine {

    private final AnchorRatingTemplateRepository templateRepository;

    public Optional<AnchorRatingTemplate> findActiveTemplate() {
        return templateRepository.findFirstByActiveTrueOrderByVersionDesc();
    }

    public Map<String, Object> activeConfig() {
        return findActiveTemplate()
                .map(AnchorRatingTemplate::getConfigJson)
                .filter(cfg -> cfg != null && !cfg.isEmpty())
                .orElseGet(AnchorRatingPolicyEngine::defaultConfig);
    }

    public List<String> questionKeys(Map<String, Object> config) {
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> q : questions(config)) {
            Object key = q.get("key");
            if (key != null && !String.valueOf(key).isBlank()) {
                keys.add(String.valueOf(key).trim());
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> questionsForUi() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> q : questions(activeConfig())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", q.get("key"));
            row.put("label", q.get("label"));
            row.put("hint", q.get("hint"));
            List<Map<String, Object>> options = new ArrayList<>();
            Object rawOpts = q.get("options");
            if (rawOpts instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    Map<String, Object> opt = new LinkedHashMap<>();
                    opt.put("value", m.get("value"));
                    opt.put("label", m.get("label"));
                    options.add(opt);
                }
            }
            row.put("options", options);
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> ratingBandsForUi() {
        return ratingBands(activeConfig());
    }

    public Map<String, Object> computeBlock(
            Map<String, Object> answers,
            Map<String, Object> comments,
            boolean requireComplete) {
        return computeBlock(activeConfig(), answers, comments);
    }

    public static Map<String, Object> computeBlock(
            Map<String, Object> config,
            Map<String, Object> answers,
            Map<String, Object> comments) {
        int score = 0;
        for (Map<String, Object> q : questions(config)) {
            String key = String.valueOf(q.get("key"));
            String value = stringVal(answers, key);
            score += scoreQuestion(q, value);
        }
        String rating = ratingFromScore(score, config);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("answers", new LinkedHashMap<>(answers != null ? answers : Map.of()));
        block.put("comments", new LinkedHashMap<>(comments != null ? comments : Map.of()));
        block.put("score", score);
        block.put("creditRating", rating);
        return block;
    }

    static Map<String, Object> defaultConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("questions", defaultQuestions());
        cfg.put("ratingBands", defaultRatingBands());
        return cfg;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> questions(Map<String, Object> config) {
        Object raw = config.get("questions");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
            if (!out.isEmpty()) return out;
        }
        return defaultQuestions();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> ratingBands(Map<String, Object> config) {
        Object raw = config.get("ratingBands");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
            if (!out.isEmpty()) return out;
        }
        return defaultRatingBands();
    }

    private static int scoreQuestion(Map<String, Object> question, String value) {
        if (value.isBlank()) return 0;
        Object rawOpts = question.get("options");
        if (!(rawOpts instanceof List<?> list)) return 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String optVal = String.valueOf(m.get("value")).trim().toUpperCase();
            if (!optVal.equals(value)) continue;
            Object score = m.get("score");
            if (score instanceof Number n) return n.intValue();
            try {
                return Integer.parseInt(String.valueOf(score));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static String ratingFromScore(int score, Map<String, Object> config) {
        List<Map<String, Object>> bands = new ArrayList<>(ratingBands(config));
        bands.sort((a, b) -> Integer.compare(intVal(b.get("minScore"), 0), intVal(a.get("minScore"), 0)));
        for (Map<String, Object> band : bands) {
            int min = intVal(band.get("minScore"), 0);
            int max = intVal(band.get("maxScore"), 100);
            if (score >= min && score <= max) {
                return String.valueOf(band.getOrDefault("rating", "D"));
            }
        }
        if (score >= 80) return "A";
        if (score >= 65) return "B";
        if (score >= 50) return "C";
        return "D";
    }

    private static int intVal(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String stringVal(Map<String, Object> m, String key) {
        if (m == null) return "";
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v).trim().toUpperCase();
    }

    private static List<Map<String, Object>> defaultRatingBands() {
        return List.of(
                band("A", 80, 100, "A — Strong"),
                band("B", 65, 79, "B — Satisfactory"),
                band("C", 50, 64, "C — Refer / manual review"),
                band("D", 0, 49, "D — Not acceptable"));
    }

    private static Map<String, Object> band(String rating, int min, int max, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rating", rating);
        m.put("minScore", min);
        m.put("maxScore", max);
        m.put("label", label);
        return m;
    }

    private static List<Map<String, Object>> defaultQuestions() {
        List<Map<String, Object>> qs = new ArrayList<>();
        qs.add(q("externalCreditRating", "External credit rating (if available)",
                "Objective — agency rating or not rated.",
                opts(
                        o("AAA", "AAA", 17), o("AA", "AA", 17), o("A", "A", 15), o("BBB", "BBB", 12),
                        o("BB", "BB", 8), o("B", "B", 5), o("C", "C / below investment grade", 0),
                        o("D", "D / default", 0), o("NOT_RATED", "Not rated externally", 6))));
        qs.add(q("financialPerformance", "Financial performance (reviewed financials)", null,
                opts(o("STRONG", "Strong — stable revenue and margins", 15),
                        o("SATISFACTORY", "Satisfactory", 10),
                        o("WEAK", "Weak — declining or volatile", 4),
                        o("DISTRESSED", "Distressed", 0))));
        qs.add(q("businessVintage", "Business vintage", null,
                opts(o("GTE_10", "10+ years", 12), o("Y5_9", "5–9 years", 9),
                        o("Y3_4", "3–4 years", 5), o("LT_3", "Under 3 years", 0))));
        qs.add(q("gstCompliance", "GST / statutory compliance", null,
                opts(o("FULL", "Fully compliant", 12), o("MINOR_DELAYS", "Minor filing delays", 7),
                        o("SIGNIFICANT_GAPS", "Significant gaps", 0))));
        qs.add(q("industryRisk", "Industry / sector risk", null,
                opts(o("LOW", "Low", 10), o("MEDIUM", "Medium", 6), o("HIGH", "High", 2))));
        qs.add(q("adverseNewsFlow", "Adverse news flow", "Subjective — media, regulatory, or market signals.",
                opts(o("NONE", "None identified", 12), o("MINOR", "Minor / isolated", 6),
                        o("MATERIAL", "Material adverse news", 0))));
        qs.add(q("legalLitigation", "Legal / litigation history", null,
                opts(o("NONE", "None", 10), o("RESOLVED", "Resolved matters only", 6),
                        o("ONGOING_MATERIAL", "Ongoing material litigation", 0))));
        qs.add(q("managementTrackRecord", "Management track record", null,
                opts(o("STRONG", "Strong — proven leadership", 12), o("ADEQUATE", "Adequate", 8),
                        o("CONCERNS", "Concerns identified", 2))));
        return qs;
    }

    private static Map<String, Object> q(String key, String label, String hint, List<Map<String, Object>> options) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        if (hint != null) m.put("hint", hint);
        m.put("options", options);
        return m;
    }

    private static Map<String, Object> o(String value, String label, int score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        m.put("score", score);
        return m;
    }

    private static List<Map<String, Object>> opts(Map<String, Object>... items) {
        return List.of(items);
    }
}
