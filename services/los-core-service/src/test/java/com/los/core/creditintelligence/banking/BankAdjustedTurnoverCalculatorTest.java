package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import com.los.core.creditintelligence.banking.service.BankAdjustedTurnoverCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankAdjustedTurnoverCalculatorTest {

    private BankAdjustedTurnoverCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new BankAdjustedTurnoverCalculator();
    }

    @Test
    void excludesLoanDisbursementSelfTransferAndIncludesCustomerReceipt() {
        LocalDate asOf = LocalDate.now();
        List<CiBankTransaction> txns = List.of(
                credit(asOf.minusDays(10), "100000", TxnCategory.CUSTOMER_RECEIPT, true, false),
                credit(asOf.minusDays(20), "500000", TxnCategory.LOAN_DISBURSEMENT, false, false),
                credit(asOf.minusDays(30), "100000", TxnCategory.SELF_TRANSFER, false, true),
                credit(asOf.minusDays(40), "50000", TxnCategory.OTHER_OPERATING, true, false));

        var result = calculator.calculate(txns, 12, asOf, 0.5);
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.PASS.name());
        assertThat(result.adjustedCredits()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(result.excludedIds()).hasSize(2);
    }

    @Test
    void lowClassificationCoverageYieldsDi() {
        LocalDate asOf = LocalDate.now();
        List<CiBankTransaction> txns = List.of(
                credit(asOf.minusDays(5), "100", TxnCategory.UNKNOWN, false, false),
                credit(asOf.minusDays(6), "100", TxnCategory.UNKNOWN, false, false));
        var result = calculator.calculate(txns, 3, asOf, 0.5);
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.DATA_INSUFFICIENT.name());
    }

    private static CiBankTransaction credit(
            LocalDate date, String amt, TxnCategory cat, boolean receipt, boolean self) {
        return CiBankTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bankAccountId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .transactionDate(date)
                .amount(new BigDecimal(amt))
                .direction(TxnDirection.CREDIT.name())
                .category(cat.name())
                .businessReceiptFlag(receipt)
                .selfTransferFlag(self)
                .duplicateStatus("UNIQUE")
                .build();
    }
}
