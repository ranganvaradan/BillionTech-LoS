package com.los.core.creditintelligence.gst.domain;

/**
 * Canonical GST filing status. Mapping version:
 * {@link #GST_FILING_STATUS_NORMALIZATION_V1}.
 */
public enum GstFilingStatus {
    FILED,
    NOT_FILED,
    LATE_FILED,
    PENDING,
    NOT_APPLICABLE,
    UNKNOWN;

    public static final String GST_FILING_STATUS_NORMALIZATION_V1 = "GST_FILING_STATUS_NORMALIZATION_V1";
}
