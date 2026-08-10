package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.CiScoreResult;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.domain.PolicyOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Execute scorecard from package JSON components/bands.
 * Missing critical components → DI/REFER, never silent zero points.
 */
@Service
public class ScorecardDefinitionExecutor {

    @SuppressWarnings("unchecked")
    public CiScoreResult execute(Map<String, Object> scorecardDef, PolicyEvaluationInput input) {
        if (scorecardDef == null || scorecardDef.isEmpty()) {
            return CiScoreResult.builder()
                    .scorecardCode("NONE")
                    .score(null)
                    .grade(PolicyOutcome.NOT_APPLICABLE.name())
                    .reasonCodes(List.of("NO_SCORECARD"))
                    .dataCompleteness(BigDecimal.ZERO)
                    .componentResults(List.of())
                    .build();
        }
        String code = String.valueOf(scorecardDef.getOrDefault("scorecardCode", "DEFAULT"));
        List<Object> components = scorecardDef.get("components") instanceof List<?> l
                ? new ArrayList<>(l) : List.of();
        List<Object> bands = scorecardDef.get("bands") instanceof List<?> l
                ? new ArrayList<>(l) : List.of();

        List<Object> componentResults = new ArrayList<>();
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal weightUsed = BigDecimal.ZERO;
        BigDecimal weightUnavailable = BigDecimal.ZERO;
        List<Object> reasonCodes = new ArrayList<>();
        boolean anyCriticalMissing = false;

        for (Object c : components) {
            if (!(c instanceof Map<?, ?> cm)) {
                continue;
            }
            Map<String, Object> comp = (Map<String, Object>) cm;
            String metricPath = String.valueOf(comp.getOrDefault("metric", comp.get("input")));
            boolean critical = Boolean.TRUE.equals(comp.get("critical"));
            BigDecimal weight = toBd(comp.get("weight"), BigDecimal.ONE);
            Object raw = resolveMetric(input, metricPath);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metric", metricPath);
            row.put("weight", weight);
            row.put("critical", critical);

            if (raw == null || isMissing(raw)) {
                row.put("status", "MISSING");
                row.put("points", null);
                weightUnavailable = weightUnavailable.add(weight);
                if (critical) {
                    anyCriticalMissing = true;
                    reasonCodes.add("CRITICAL_COMPONENT_MISSING:" + metricPath);
                } else {
                    reasonCodes.add("COMPONENT_MISSING:" + metricPath);
                }
            } else {
                BigDecimal points = scoreComponent(comp, raw);
                row.put("status", "SCORED");
                row.put("points", points);
                row.put("raw", raw);
                score = score.add(points.multiply(weight));
                weightUsed = weightUsed.add(weight);
            }
            componentResults.add(row);
        }

        BigDecimal totalWeight = weightUsed.add(weightUnavailable);
        BigDecimal completeness = totalWeight.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : weightUsed.divide(totalWeight, 4, RoundingMode.HALF_UP);

        String grade;
        if (anyCriticalMissing) {
            grade = PolicyOutcome.DATA_INSUFFICIENT.name();
            score = null;
            reasonCodes.add("SCORECARD_DI_CRITICAL_MISSING");
        } else if (completeness.compareTo(new BigDecimal("0.5")) < 0) {
            grade = PolicyOutcome.REFER.name();
            reasonCodes.add("SCORECARD_REFER_INCOMPLETE");
        } else {
            grade = bandGrade(bands, score);
        }

        return CiScoreResult.builder()
                .scorecardCode(code)
                .score(score)
                .grade(grade)
                .componentResults(componentResults)
                .weightUsed(weightUsed)
                .weightUnavailable(weightUnavailable)
                .dataCompleteness(completeness)
                .reasonCodes(reasonCodes)
                .build();
    }

    @SuppressWarnings("unchecked")
    private BigDecimal scoreComponent(Map<String, Object> comp, Object raw) {
        if (comp.get("points") instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        Object bands = comp.get("bands");
        Double value = toDouble(raw);
        if (value == null || !(bands instanceof List<?> list)) {
            return BigDecimal.ZERO;
        }
        for (Object b : list) {
            if (!(b instanceof Map<?, ?> bm)) {
                continue;
            }
            Double min = toDouble(bm.get("min"));
            Double max = toDouble(bm.get("max"));
            BigDecimal pts = toBd(bm.get("points"), BigDecimal.ZERO);
            if (min != null && value < min) {
                continue;
            }
            if (max != null && value > max) {
                continue;
            }
            return pts;
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private String bandGrade(List<Object> bands, BigDecimal score) {
        if (score == null || bands == null || bands.isEmpty()) {
            return "UNBANDED";
        }
        double s = score.doubleValue();
        for (Object b : bands) {
            if (!(b instanceof Map<?, ?> bm)) {
                continue;
            }
            Double min = toDouble(bm.get("min"));
            Double max = toDouble(bm.get("max"));
            if (min != null && s < min) {
                continue;
            }
            if (max != null && s > max) {
                continue;
            }
            return String.valueOf(bm.get("grade") == null ? "BAND" : bm.get("grade"));
        }
        return "UNBANDED";
    }

    private Object resolveMetric(PolicyEvaluationInput input, String path) {
        if (path == null) {
            return null;
        }
        if (input.metrics().containsKey(path)) {
            return unwrap(input.metrics().get(path));
        }
        if (input.facts().containsKey(path)) {
            return unwrap(input.facts().get(path));
        }
        return null;
    }

    private Object unwrap(Object raw) {
        if (raw instanceof Map<?, ?> mm) {
            Object status = mm.get("dataStatus");
            if (status != null && ("MISSING".equalsIgnoreCase(String.valueOf(status))
                    || "DATA_INSUFFICIENT".equalsIgnoreCase(String.valueOf(status)))) {
                return null;
            }
            Object v = mm.get("value") != null ? mm.get("value") : mm.get("v");
            return v;
        }
        return raw;
    }

    private boolean isMissing(Object raw) {
        return raw == null;
    }

    private BigDecimal toBd(Object o, BigDecimal def) {
        if (o == null) {
            return def;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof Map<?, ?> mm) {
            return toDouble(mm.get("value") != null ? mm.get("value") : mm.get("v"));
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
