package com.los.core.creditintelligence.gst.util;

import com.los.core.creditintelligence.gst.domain.GstFilingStatus;

import java.util.Locale;

/**
 * Maps provider filing status strings to {@link GstFilingStatus}
 * under {@link GstFilingStatus#GST_FILING_STATUS_NORMALIZATION_V1}.
 */
public final class GstFilingStatusNormalizer {

    public record NormalizedFiling(GstFilingStatus status, Integer delayDays, String raw) {
    }

    private GstFilingStatusNormalizer() {
    }

    public static NormalizedFiling normalize(String rawStatus) {
        return normalize(rawStatus, null);
    }

    /**
     * @param delayDays optional delay vs due date; when status is Filed and delayDays &gt; 0 → LATE_FILED
     */
    public static NormalizedFiling normalize(String rawStatus, Integer delayDays) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return new NormalizedFiling(GstFilingStatus.UNKNOWN, delayDays, rawStatus);
        }
        String s = rawStatus.trim().toLowerCase(Locale.ROOT);

        if (isNotApplicable(s)) {
            return new NormalizedFiling(GstFilingStatus.NOT_APPLICABLE, delayDays, rawStatus);
        }
        if (isPending(s)) {
            return new NormalizedFiling(GstFilingStatus.PENDING, delayDays, rawStatus);
        }
        if (isNotFiled(s)) {
            return new NormalizedFiling(GstFilingStatus.NOT_FILED, delayDays, rawStatus);
        }
        if (isLate(s)) {
            int d = delayDays != null && delayDays > 0 ? delayDays : (delayDays == null ? 1 : delayDays);
            return new NormalizedFiling(GstFilingStatus.LATE_FILED, d > 0 ? d : 1, rawStatus);
        }
        if (isFiled(s)) {
            if (delayDays != null && delayDays > 0) {
                return new NormalizedFiling(GstFilingStatus.LATE_FILED, delayDays, rawStatus);
            }
            return new NormalizedFiling(GstFilingStatus.FILED, delayDays != null ? delayDays : 0, rawStatus);
        }
        // Unknown ≠ filed
        return new NormalizedFiling(GstFilingStatus.UNKNOWN, delayDays, rawStatus);
    }

    public static boolean isFiledLike(GstFilingStatus status) {
        return status == GstFilingStatus.FILED || status == GstFilingStatus.LATE_FILED;
    }

    private static boolean isFiled(String s) {
        return ("filed".equals(s) || "filed successfully".equals(s) || s.contains("filed"))
                && !s.contains("not") && !s.contains("unfiled") && !s.contains("late");
    }

    private static boolean isLate(String s) {
        return s.contains("late") || "delayed".equals(s) || s.contains("filed late");
    }

    private static boolean isNotFiled(String s) {
        return "not filed".equals(s) || "unfiled".equals(s) || "not-filed".equals(s)
                || "nofile".equals(s) || "no file".equals(s);
    }

    private static boolean isPending(String s) {
        return "pending".equals(s) || "in progress".equals(s) || "processing".equals(s);
    }

    private static boolean isNotApplicable(String s) {
        return "na".equals(s) || "n/a".equals(s) || "not applicable".equals(s)
                || "not_applicable".equals(s);
    }
}
