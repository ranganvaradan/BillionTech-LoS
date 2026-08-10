package com.los.core.creditintelligence.tax;

import com.los.core.creditintelligence.tax.util.TaxYearUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaxYearUtilsTest {

    @Test
    void fyToAy_andAyToFy_roundTrip() {
        assertThat(TaxYearUtils.fyToAy("2024-25")).contains("2025-26");
        assertThat(TaxYearUtils.ayToFy("2025-26")).contains("2024-25");
        assertThat(TaxYearUtils.fyToAy("FY2024-25")).contains("2025-26");
        assertThat(TaxYearUtils.isValidFyAyPair("2024-25", "2025-26")).isTrue();
        assertThat(TaxYearUtils.isValidFyAyPair("2024-25", "2024-25")).isFalse();
    }

    @Test
    void trailingYears_newestFirst() {
        assertThat(TaxYearUtils.trailingFinancialYears("2024-25", 3))
                .containsExactly("2024-25", "2023-24", "2022-23");
        assertThat(TaxYearUtils.trailingAssessmentYears("2025-26", 2))
                .containsExactly("2025-26", "2024-25");
    }

    @Test
    void compareYearLabels() {
        assertThat(TaxYearUtils.compareYearLabels("2025-26", "2024-25")).isPositive();
        assertThat(TaxYearUtils.compareYearLabels("2024-25", "2024-25")).isZero();
    }
}
