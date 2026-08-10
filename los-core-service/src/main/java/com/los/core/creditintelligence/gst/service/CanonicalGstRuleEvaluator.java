package com.los.core.creditintelligence.gst.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.domain.RuleOutcome;
import com.los.core.creditintelligence.gst.domain.CiGstRegistration;
import com.los.core.creditintelligence.gst.domain.GstFilingStatus;
import com.los.core.creditintelligence.gst.domain.GstMetricOutcome;
import com.los.core.creditintelligence.gst.provider.KarzaGstCanonicalExtractor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow-only GST rules. Production CreditControl gap defaults remain unchanged.
 */
@Component
public class CanonicalGstRuleEvaluator {

    public static final String DQ_GST_AVAILABLE = "DQ_GST_AVAILABLE";
    public static final String HARD_GST_REGISTRATION_STATUS = "HARD_GST_REGISTRATION_STATUS";
    public static final String GST_FILING_TIMELINESS = "GST_FILING_TIMELINESS";
    public static final String GST_MISSING_RETURNS = "GST_MISSING_RETURNS";
    public static final String XSRC_GSTR1_GSTR3B_VARIANCE = "XSRC_GSTR1_GSTR3B_VARIANCE";
    public static final String GST_TURNOVER_ELIGIBILITY = "GST_TURNOVER_ELIGIBILITY";

    public record RuleEvalResult(
            String ruleId,
            String ruleVersion,
            String outcome,
            Object value,
            Object threshold,
            Map<String, Object> versions,
            Map<String, Object> evidence) {
    }

    public List<RuleEvalResult> evaluateAll(
            List<CiGstRegistration> registrations,
            Map<String, CiMetricResult> metricsByCode,
            BigDecimal turnoverEligibilityThreshold,
            int timelinessMinScore,
            int missingMaxAllowed) {

        List<RuleEvalResult> out = new ArrayList<>();
        out.add(evaluateDqAvailable(registrations, metricsByCode));
        out.add(evaluateRegistrationStatus(registrations));
        out.add(evaluateTimeliness(metricsByCode, timelinessMinScore));
        out.add(evaluateMissingReturns(metricsByCode, missingMaxAllowed));
        out.add(evaluateVariance(metricsByCode));
        out.add(evaluateTurnoverEligibility(metricsByCode, turnoverEligibilityThreshold));
        return out;
    }

    public RuleEvalResult evaluateDqAvailable(
            List<CiGstRegistration> registrations, Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        boolean available = registrations != null && !registrations.isEmpty();
        CiMetricResult t12 = metricsByCode != null ? metricsByCode.get(GstMetricService.TRAILING_12M) : null;
        if (!available) {
            return new RuleEvalResult(DQ_GST_AVAILABLE, DQ_GST_AVAILABLE + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), false, true, versions,
                    Map.of("reason", "NO_REGISTRATION"));
        }
        String outcome = RuleOutcome.PASS.name();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("registrationCount", registrations.size());
        if (t12 != null && GstMetricOutcome.DATA_INSUFFICIENT.name().equals(t12.getOutcome())) {
            evidence.put("trailing12m", "DATA_INSUFFICIENT");
        }
        return new RuleEvalResult(DQ_GST_AVAILABLE, DQ_GST_AVAILABLE + "_V1",
                outcome, true, true, versions, evidence);
    }

    public RuleEvalResult evaluateRegistrationStatus(List<CiGstRegistration> registrations) {
        Map<String, Object> versions = frozenVersions();
        if (registrations == null || registrations.isEmpty()) {
            return new RuleEvalResult(HARD_GST_REGISTRATION_STATUS, HARD_GST_REGISTRATION_STATUS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, "ACTIVE", versions,
                    Map.of("reason", "MISSING_REGISTRATION"));
        }
        boolean anyActive = false;
        boolean anyInactive = false;
        for (CiGstRegistration r : registrations) {
            String s = r.getRegistrationStatus() != null
                    ? r.getRegistrationStatus().toUpperCase() : "UNKNOWN";
            if ("ACTIVE".equals(s)) {
                anyActive = true;
            } else if ("INACTIVE".equals(s) || "CANCELLED".equals(s) || "SUSPENDED".equals(s)) {
                anyInactive = true;
            }
        }
        if (anyActive && !anyInactive) {
            return new RuleEvalResult(HARD_GST_REGISTRATION_STATUS, HARD_GST_REGISTRATION_STATUS + "_V1",
                    RuleOutcome.PASS.name(), "ACTIVE", "ACTIVE", versions,
                    Map.of("status", "ACTIVE"));
        }
        if (anyInactive) {
            return new RuleEvalResult(HARD_GST_REGISTRATION_STATUS, HARD_GST_REGISTRATION_STATUS + "_V1",
                    RuleOutcome.FAIL.name(), "INACTIVE", "ACTIVE", versions,
                    Map.of("status", "INACTIVE"));
        }
        return new RuleEvalResult(HARD_GST_REGISTRATION_STATUS, HARD_GST_REGISTRATION_STATUS + "_V1",
                RuleOutcome.DATA_INSUFFICIENT.name(), "UNKNOWN", "ACTIVE", versions,
                Map.of("reason", "STATUS_UNKNOWN"));
    }

    public RuleEvalResult evaluateTimeliness(Map<String, CiMetricResult> metricsByCode, int minScore) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(GstMetricService.TIMELINESS) : null;
        if (m == null || GstMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(GST_FILING_TIMELINESS, GST_FILING_TIMELINESS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, minScore, versions,
                    Map.of("reason", "METRIC_MISSING"));
        }
        Integer score = unwrapInt(m.getValue());
        if (score == null) {
            return new RuleEvalResult(GST_FILING_TIMELINESS, GST_FILING_TIMELINESS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, minScore, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = score >= minScore ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(GST_FILING_TIMELINESS, GST_FILING_TIMELINESS + "_V1",
                outcome, score, minScore, versions,
                Map.of("score", score, "minScore", minScore));
    }

    public RuleEvalResult evaluateMissingReturns(Map<String, CiMetricResult> metricsByCode, int maxAllowed) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(GstMetricService.MISSING_12M) : null;
        if (m == null || GstMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(GST_MISSING_RETURNS, GST_MISSING_RETURNS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, maxAllowed, versions,
                    Map.of("reason", "METRIC_MISSING"));
        }
        Integer count = unwrapInt(m.getValue());
        if (count == null) {
            return new RuleEvalResult(GST_MISSING_RETURNS, GST_MISSING_RETURNS + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, maxAllowed, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = count <= maxAllowed ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(GST_MISSING_RETURNS, GST_MISSING_RETURNS + "_V1",
                outcome, count, maxAllowed, versions,
                Map.of("missingCount", count, "maxAllowed", maxAllowed));
    }

    public RuleEvalResult evaluateVariance(Map<String, CiMetricResult> metricsByCode) {
        Map<String, Object> versions = frozenVersions();
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(GstMetricService.VARIANCE) : null;
        if (m == null || GstMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(XSRC_GSTR1_GSTR3B_VARIANCE, XSRC_GSTR1_GSTR3B_VARIANCE + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, null, versions,
                    Map.of("reason", "METRIC_DATA_INSUFFICIENT"));
        }
        String metricOutcome = m.getOutcome();
        String ruleOutcome;
        if (GstMetricOutcome.MATCH.name().equals(metricOutcome)
                || GstMetricOutcome.ACCEPTABLE_VARIANCE.name().equals(metricOutcome)) {
            ruleOutcome = RuleOutcome.PASS.name();
        } else if (GstMetricOutcome.MATERIAL_VARIANCE.name().equals(metricOutcome)
                || GstMetricOutcome.CONFLICT.name().equals(metricOutcome)) {
            ruleOutcome = RuleOutcome.FAIL.name();
        } else {
            ruleOutcome = RuleOutcome.REFER.name();
        }
        return new RuleEvalResult(XSRC_GSTR1_GSTR3B_VARIANCE, XSRC_GSTR1_GSTR3B_VARIANCE + "_V1",
                ruleOutcome, metricOutcome, null, versions,
                Map.of("varianceOutcome", metricOutcome, "value", m.getValue() != null ? m.getValue() : Map.of()));
    }

    public RuleEvalResult evaluateTurnoverEligibility(
            Map<String, CiMetricResult> metricsByCode, BigDecimal threshold) {
        Map<String, Object> versions = frozenVersions();
        BigDecimal thr = threshold != null ? threshold : new BigDecimal("50000000");
        CiMetricResult m = metricsByCode != null ? metricsByCode.get(GstMetricService.TRAILING_12M) : null;
        if (m == null || GstMetricOutcome.DATA_INSUFFICIENT.name().equals(m.getOutcome())) {
            return new RuleEvalResult(GST_TURNOVER_ELIGIBILITY, GST_TURNOVER_ELIGIBILITY + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, thr, versions,
                    Map.of("reason", "TRAILING_12M_INSUFFICIENT",
                            "note", "Shadow only — production still uses CreditControl gap default"));
        }
        BigDecimal value = unwrapBd(m.getValue());
        if (value == null) {
            return new RuleEvalResult(GST_TURNOVER_ELIGIBILITY, GST_TURNOVER_ELIGIBILITY + "_V1",
                    RuleOutcome.DATA_INSUFFICIENT.name(), null, thr, versions,
                    Map.of("reason", "VALUE_NULL"));
        }
        String outcome = value.compareTo(thr) >= 0 ? RuleOutcome.PASS.name() : RuleOutcome.FAIL.name();
        return new RuleEvalResult(GST_TURNOVER_ELIGIBILITY, GST_TURNOVER_ELIGIBILITY + "_V1",
                outcome, value, thr, versions,
                Map.of("trailing12m", value.toPlainString(), "threshold", thr.toPlainString(),
                        "shadowOnly", true));
    }

    public static Map<String, Object> frozenVersions() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("parserVersion", KarzaGstCanonicalExtractor.PARSER_VERSION);
        v.put("normalizerVersion", GstNormalizationService.NORMALIZER_VERSION);
        v.put("metricVersion", GstMetricService.METRIC_VERSION);
        v.put("filingStatusNorm", GstFilingStatus.GST_FILING_STATUS_NORMALIZATION_V1);
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

    static BigDecimal unwrapBd(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        Object raw = value.get("v");
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }
}
