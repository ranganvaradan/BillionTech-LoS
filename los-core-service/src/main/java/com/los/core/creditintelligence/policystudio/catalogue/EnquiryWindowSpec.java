package com.los.core.creditintelligence.policystudio.catalogue;

/**
 * Canonical enquiry / inquiry window kinds for bureau catalogue binding.
 * Distinct windows must not collapse into each other.
 */
public record EnquiryWindowSpec(String kind, Long months, Long days) {

    public static final String CURRENT_MONTH = "CURRENT_MONTH";
    public static final String LAST_30_DAYS = "LAST_30_DAYS";
    public static final String LAST_90_DAYS = "LAST_90_DAYS";
    public static final String LAST_3_MONTHS = "LAST_3_MONTHS";
    public static final String LAST_6_MONTHS = "LAST_6_MONTHS";
    public static final String LAST_12_MONTHS = "LAST_12_MONTHS";

    public static EnquiryWindowSpec currentMonth() {
        return new EnquiryWindowSpec(CURRENT_MONTH, 0L, null);
    }

    public static EnquiryWindowSpec lastDays(long days) {
        if (days == 30) {
            return new EnquiryWindowSpec(LAST_30_DAYS, null, 30L);
        }
        if (days == 90) {
            return new EnquiryWindowSpec(LAST_90_DAYS, null, 90L);
        }
        return new EnquiryWindowSpec("LAST_" + days + "_DAYS", null, days);
    }

    public static EnquiryWindowSpec lastMonths(long months) {
        if (months == 3) {
            return new EnquiryWindowSpec(LAST_3_MONTHS, 3L, null);
        }
        if (months == 6) {
            return new EnquiryWindowSpec(LAST_6_MONTHS, 6L, null);
        }
        if (months == 12) {
            return new EnquiryWindowSpec(LAST_12_MONTHS, 12L, null);
        }
        return new EnquiryWindowSpec("LAST_" + months + "_MONTHS", months, null);
    }

    public String displayLabel() {
        return switch (kind) {
            case CURRENT_MONTH -> "current month";
            case LAST_30_DAYS -> "last 30 days";
            case LAST_90_DAYS -> "last 90 days";
            case LAST_3_MONTHS -> "last 3 months";
            case LAST_6_MONTHS -> "last 6 months";
            case LAST_12_MONTHS -> "last 12 months";
            default -> kind;
        };
    }
}
