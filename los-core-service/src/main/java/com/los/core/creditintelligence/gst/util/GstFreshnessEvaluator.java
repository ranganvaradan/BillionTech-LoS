package com.los.core.creditintelligence.gst.util;

import com.los.core.creditintelligence.gst.domain.GstFreshnessPolicy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evaluates whether GST filing data is fresh enough relative to expected
 * latest completed return period + configurable lag (default 20 days).
 * Policy: {@link GstFreshnessPolicy#GST_FRESHNESS_POLICY_V1}.
 */
public final class GstFreshnessEvaluator {

    public record FreshnessResult(
            boolean fresh,
            YearMonth expectedLatestPeriod,
            YearMonth actualLatestPeriod,
            int lagDays,
            String policyVersion,
            Map<String, Object> evidence) {
    }

    private GstFreshnessEvaluator() {
    }

    public static FreshnessResult evaluate(LocalDate asOf, YearMonth actualLatestFiledPeriod, int filingLagDays) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        int lag = filingLagDays >= 0 ? filingLagDays : 20;
        YearMonth expected = GstPeriodUtils.expectedLatestCompletedReturnPeriod(date, lag);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("asOf", date.toString());
        evidence.put("filingLagDays", lag);
        evidence.put("expectedLatestPeriod", expected.toString());
        evidence.put("actualLatestPeriod", actualLatestFiledPeriod != null
                ? actualLatestFiledPeriod.toString() : null);
        evidence.put("policy", GstFreshnessPolicy.GST_FRESHNESS_POLICY_V1);

        if (actualLatestFiledPeriod == null) {
            evidence.put("reason", "NO_FILED_PERIOD");
            return new FreshnessResult(false, expected, null, lag,
                    GstFreshnessPolicy.GST_FRESHNESS_POLICY_V1, evidence);
        }

        boolean fresh = !actualLatestFiledPeriod.isBefore(expected);
        evidence.put("reason", fresh ? "WITHIN_EXPECTED" : "STALE_VS_EXPECTED");
        return new FreshnessResult(fresh, expected, actualLatestFiledPeriod, lag,
                GstFreshnessPolicy.GST_FRESHNESS_POLICY_V1, evidence);
    }
}
