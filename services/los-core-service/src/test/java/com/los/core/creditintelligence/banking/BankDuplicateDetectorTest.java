package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import com.los.core.creditintelligence.banking.service.BankDuplicateDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankDuplicateDetectorTest {

    private BankDuplicateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BankDuplicateDetector();
    }

    @Test
    void detectsByProviderTxnId() {
        LocalDate d = LocalDate.of(2026, 1, 10);
        List<CiBankTransaction> txns = List.of(
                txn(d, "100", TxnDirection.CREDIT, "PID-1", null, "A", null),
                txn(d, "100", TxnDirection.CREDIT, "PID-1", null, "A", null));
        var groups = detector.detectIndexed(txns);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).matchingBasis()).isEqualTo("PROVIDER_TXN_ID");
    }

    @Test
    void detectsByDateAmountDirectionUtr() {
        LocalDate d = LocalDate.of(2026, 1, 10);
        List<CiBankTransaction> txns = List.of(
                txn(d, "50", TxnDirection.DEBIT, null, "UTR999", "PAY", null),
                txn(d, "50", TxnDirection.DEBIT, null, "UTR999", "PAY", null));
        var groups = detector.detectIndexed(txns);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).matchingBasis()).isEqualTo("DATE_AMOUNT_DIRECTION_UTR");
    }

    @Test
    void neverDuplicatesOnDateAndAmountAlone() {
        LocalDate d = LocalDate.of(2026, 1, 10);
        List<CiBankTransaction> txns = List.of(
                txn(d, "50", TxnDirection.DEBIT, null, null, "VENDOR A", new BigDecimal("1000")),
                txn(d, "50", TxnDirection.DEBIT, null, null, "VENDOR B", new BigDecimal("900")));
        var groups = detector.detectIndexed(txns);
        assertThat(groups).isEmpty();
    }

    private static CiBankTransaction txn(
            LocalDate date, String amount, TxnDirection dir,
            String providerId, String utr, String narr, BigDecimal bal) {
        return CiBankTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bankAccountId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .transactionDate(date)
                .amount(new BigDecimal(amount))
                .direction(dir.name())
                .providerTransactionId(providerId)
                .utrReference(utr)
                .descriptionRaw(narr)
                .descriptionNormalized(narr)
                .balanceAfter(bal)
                .build();
    }
}
