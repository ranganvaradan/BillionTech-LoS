package com.los.core.creditintelligence.reconciliation.domain;

public enum PeriodAlignmentStrategy {
    EXACT_PERIOD,
    FINANCIAL_YEAR,
    TRAILING_12_MONTHS,
    YTD_COMPARABLE,
    COMMON_OVERLAP,
    LATEST_COMPLETE_COMMON_PERIOD
}
