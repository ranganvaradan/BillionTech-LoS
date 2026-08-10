package com.los.core.creditintelligence.gst;

import com.los.core.creditintelligence.gst.util.GstPeriodUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GstPeriodUtilsTest {

    @Test
    void parseMmyyyy_aprilAndMarchBoundaries() {
        assertThat(GstPeriodUtils.parseMmyyyyToYyyyMm("042026")).contains("2026-04");
        assertThat(GstPeriodUtils.parseMmyyyyToYyyyMm("032026")).contains("2026-03");
        assertThat(GstPeriodUtils.parseMmyyyyToYyyyMm("132026")).isEmpty();
        assertThat(GstPeriodUtils.parseMmyyyyToYyyyMm("2026-04")).contains("2026-04");
    }

    @Test
    void financialYear_aprMar() {
        assertThat(GstPeriodUtils.financialYearLabel(YearMonth.of(2026, 4))).isEqualTo("2026-27");
        assertThat(GstPeriodUtils.financialYearLabel(YearMonth.of(2026, 3))).isEqualTo("2025-26");
        assertThat(GstPeriodUtils.financialYearLabel(YearMonth.of(2025, 4))).isEqualTo("2025-26");
    }

    @Test
    void trailingMonths_andFyYtd() {
        List<String> trailing = GstPeriodUtils.trailingMonths(YearMonth.of(2026, 6), 3);
        assertThat(trailing).containsExactly("2026-04", "2026-05", "2026-06");

        List<String> ytd = GstPeriodUtils.currentFyYtdPeriods(YearMonth.of(2026, 6));
        assertThat(ytd).startsWith("2026-04").endsWith("2026-06");
        assertThat(ytd).hasSize(3);

        // Jan is still FY starting previous Apr
        List<String> ytdJan = GstPeriodUtils.currentFyYtdPeriods(YearMonth.of(2026, 1));
        assertThat(ytdJan).startsWith("2025-04").endsWith("2026-01");
    }

    @Test
    void latestCompletedFy_andExpectedReturnPeriodWithLag() {
        assertThat(GstPeriodUtils.latestCompletedFyEnd(LocalDate.of(2026, 5, 1)))
                .isEqualTo(YearMonth.of(2026, 3));
        assertThat(GstPeriodUtils.latestCompletedFyEnd(LocalDate.of(2026, 2, 1)))
                .isEqualTo(YearMonth.of(2025, 3));

        // Mid June with 20-day lag: May month-end + 20 = Jun 20; before that expected is Apr
        YearMonth beforeLag = GstPeriodUtils.expectedLatestCompletedReturnPeriod(
                LocalDate.of(2026, 6, 10), 20);
        assertThat(beforeLag).isEqualTo(YearMonth.of(2026, 4));

        YearMonth afterLag = GstPeriodUtils.expectedLatestCompletedReturnPeriod(
                LocalDate.of(2026, 6, 25), 20);
        assertThat(afterLag).isEqualTo(YearMonth.of(2026, 5));
    }
}
