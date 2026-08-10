package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.BureauMetricOutcome;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.bureau.domain.LiveAccountDefinition;
import com.los.core.creditintelligence.domain.RuleOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HARD_LIVE_UNSECURED shadow rule — compares canonical live_unsecured metric to threshold.
 */
@Component
public class CanonicalBureauRuleEvaluator {

    public static final String RULE_ID = "HARD_LIVE_UNSECURED";
    public static final String RULE_VERSION = "HARD_LIVE_UNSECURED_V1";
    public static final String METRIC_VERSION = "V1";
    public static final String TAXONOMY_VERSION = BureauProductTaxonomyService.EQUIFAX_TAXONOMY_V1;
    public static final String LIVE_DEF = LiveAccountDefinition.BUREAU_LIVE_ACCOUNT_DEFINITION_V1;

    public record RuleEvalResult(
            String ruleId,
            String ruleVersion,
            String outcome,
            Integer value,
            Integer threshold,
            Map<String, Object> versions,
            Map<String, Object> evidence) {
    }

    public RuleEvalResult evaluate(CiMetricResult liveUnsecuredMetric, int threshold) {
        Map<String, Object> versions = frozenVersions();
        if (liveUnsecuredMetric == null) {
            return new RuleEvalResult(RULE_ID, RULE_VERSION, RuleOutcome.DATA_INSUFFICIENT.name(),
                    null, threshold, versions, Map.of("reason", "METRIC_MISSING"));
        }
        if (BureauMetricOutcome.DATA_INSUFFICIENT.name().equals(liveUnsecuredMetric.getOutcome())
                || RuleOutcome.DATA_INSUFFICIENT.name().equals(liveUnsecuredMetric.getOutcome())) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("reason", "METRIC_DATA_INSUFFICIENT");
            evidence.put("metricId", liveUnsecuredMetric.getId() != null
                    ? liveUnsecuredMetric.getId().toString() : null);
            return new RuleEvalResult(RULE_ID, RULE_VERSION, RuleOutcome.DATA_INSUFFICIENT.name(),
                    null, threshold, versions, evidence);
        }

        Integer value = unwrapInt(liveUnsecuredMetric.getValue());
        if (value == null) {
            return new RuleEvalResult(RULE_ID, RULE_VERSION, RuleOutcome.DATA_INSUFFICIENT.name(),
                    null, threshold, versions, Map.of("reason", "VALUE_NULL"));
        }

        String outcome = value <= threshold ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("value", value);
        evidence.put("threshold", threshold);
        evidence.put("metricCode", liveUnsecuredMetric.getMetricCode());
        evidence.put("metricVersion", liveUnsecuredMetric.getMetricVersion());
        return new RuleEvalResult(RULE_ID, RULE_VERSION, outcome, value, threshold, versions, evidence);
    }

    public static Map<String, Object> frozenVersions() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ruleVersion", RULE_VERSION);
        v.put("metricVersion", METRIC_VERSION);
        v.put("taxonomyVersion", TAXONOMY_VERSION);
        v.put("liveDefinitionVersion", LIVE_DEF);
        return v;
    }

    static Integer unwrapInt(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        Object raw = value.get("v");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return new BigDecimal(String.valueOf(raw)).intValue();
        } catch (Exception e) {
            return null;
        }
    }
}
