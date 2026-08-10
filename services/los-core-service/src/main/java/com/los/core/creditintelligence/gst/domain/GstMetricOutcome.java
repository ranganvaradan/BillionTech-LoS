package com.los.core.creditintelligence.gst.domain;

/**
 * GST metric outcomes. Extends bureau-style PASS/REFER/DI/ERROR with
 * GSTR1↔GSTR3B variance outcomes used as string values on CiMetricResult.
 */
public enum GstMetricOutcome {
    PASS,
    REFER,
    DATA_INSUFFICIENT,
    ERROR,
    MATCH,
    ACCEPTABLE_VARIANCE,
    MATERIAL_VARIANCE,
    CONFLICT
}
