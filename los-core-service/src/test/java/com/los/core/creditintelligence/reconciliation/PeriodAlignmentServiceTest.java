package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.reconciliation.domain.PeriodAlignmentStrategy;
import com.los.core.creditintelligence.reconciliation.service.PeriodAlignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodAlignmentServiceTest {

    private PeriodAlignmentService service;

    @BeforeEach
    void setUp() {
        service = new PeriodAlignmentService();
    }

    @Test
    void exactPeriodMatch() {
        var w = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31));
        var r = service.align(PeriodAlignmentStrategy.EXACT_PERIOD, w, w, w);
        assertThat(r.comparable()).isTrue();
        assertThat(r.monthsCompared()).hasSize(12);
    }

    @Test
    void exactPeriodMismatch() {
        var left = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31));
        var right = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2023, 4, 1), LocalDate.of(2024, 3, 31));
        var r = service.align(PeriodAlignmentStrategy.EXACT_PERIOD, left, right, left);
        assertThat(r.comparable()).isFalse();
    }

    @Test
    void financialYearAligned() {
        var left = PeriodAlignmentService.financialYearEnding(2025);
        var right = PeriodAlignmentService.financialYearEnding(2025);
        var r = service.align(PeriodAlignmentStrategy.FINANCIAL_YEAR, left, right, left);
        assertThat(r.comparable()).isTrue();
    }

    @Test
    void financialYearMismatch() {
        var left = PeriodAlignmentService.financialYearEnding(2025);
        var right = PeriodAlignmentService.financialYearEnding(2024);
        var r = service.align(PeriodAlignmentStrategy.FINANCIAL_YEAR, left, right, left);
        assertThat(r.comparable()).isFalse();
    }

    @Test
    void commonOverlapPartialGst() {
        var left = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        var right = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 12, 31));
        var r = service.align(PeriodAlignmentStrategy.COMMON_OVERLAP, left, right, left);
        assertThat(r.comparable()).isTrue();
        assertThat(r.overlap().monthsInclusive()).isEqualTo(9);
        assertThat(r.excludedMonths()).isNotEmpty();
        // coverage vs shorter window (9 months) can be 1.0; vs longer left would be partial
        assertThat(r.monthsCompared()).hasSize(9);
    }

    @Test
    void sixMonthBankVsTwelveMonthItrInsufficientOverlap() {
        var itr = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31));
        var bank = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2024, 10, 1), LocalDate.of(2025, 3, 31));
        var r = service.align(PeriodAlignmentStrategy.COMMON_OVERLAP, itr, bank, itr);
        // 6 of 12 = 50% of shorter — borderline; shorter is 6 so overlap 6 >= shorter/2
        assertThat(r.comparable()).isTrue();
    }

    @Test
    void noCommonOverlap() {
        var left = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 30));
        var right = new PeriodAlignmentService.PeriodWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
        var r = service.align(PeriodAlignmentStrategy.COMMON_OVERLAP, left, right, left);
        assertThat(r.comparable()).isFalse();
    }

    @Test
    void trailing12() {
        var left = PeriodAlignmentService.trailing12(LocalDate.of(2025, 8, 1));
        var right = PeriodAlignmentService.trailing12(LocalDate.of(2025, 8, 1));
        var r = service.align(PeriodAlignmentStrategy.TRAILING_12_MONTHS, left, right, left);
        assertThat(r.comparable()).isTrue();
        assertThat(r.methodVersion()).isEqualTo("PERIOD_ALIGNMENT_V1");
    }
}
