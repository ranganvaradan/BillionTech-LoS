package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import com.los.core.creditintelligence.banking.service.BankAverageDailyBalanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankAverageDailyBalanceCalculatorTest {

    private BankAverageDailyBalanceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new BankAverageDailyBalanceCalculator();
    }

    @Test
    void carryForwardProducesAverageIncludingValidZero() {
        LocalDate asOf = LocalDate.of(2026, 6, 30);
        List<CiBankTransaction> txns = new ArrayList<>();
        LocalDate start = asOf.minusMonths(3).plusDays(1);
        txns.add(txn(start, "10", TxnDirection.CREDIT, new BigDecimal("100")));
        txns.add(txn(start.plusDays(10), "100", TxnDirection.DEBIT, BigDecimal.ZERO));
        txns.add(txn(start.plusDays(20), "50", TxnDirection.CREDIT, new BigDecimal("50")));

        var result = calculator.calculate(txns, 3, asOf);
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.PASS.name());
        assertThat(result.averageDailyBalance()).isNotNull();
        assertThat(result.minimumBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void noTransactionsYieldsDataInsufficient() {
        var result = calculator.calculate(List.of(), 3, LocalDate.now());
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(result.averageDailyBalance()).isNull();
    }

    @Test
    void noBalanceSeriesYieldsDataInsufficient() {
        LocalDate asOf = LocalDate.now();
        List<CiBankTransaction> txns = List.of(
                txn(asOf.minusDays(5), "100", TxnDirection.CREDIT, null));
        var result = calculator.calculate(txns, 3, asOf);
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.DATA_INSUFFICIENT.name());
    }

    private static CiBankTransaction txn(LocalDate date, String amt, TxnDirection dir, BigDecimal bal) {
        return CiBankTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bankAccountId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .transactionDate(date)
                .amount(new BigDecimal(amt))
                .direction(dir.name())
                .balanceAfter(bal)
                .duplicateStatus("UNIQUE")
                .build();
    }
}
