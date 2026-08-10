package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import com.los.core.creditintelligence.banking.service.BankEmiObligationDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankEmiObligationDetectorTest {

    private BankEmiObligationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BankEmiObligationDetector();
    }

    @Test
    void detectsRecurringEmiWithMinOccurrences() {
        UUID tenant = UUID.randomUUID();
        UUID app = UUID.randomUUID();
        UUID acct = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusMonths(5);
        List<CiBankTransaction> txns = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            txns.add(emi(start.plusMonths(i), "45000", "NACH DEBIT HDFC BANK EMI"));
        }
        var result = detector.detect(tenant, app, acct, txns, 3, 0.7);
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.PASS.name());
        assertThat(result.monthlyObligation()).isEqualByComparingTo(new BigDecimal("45000"));
        assertThat(result.obligations()).isNotEmpty();
        assertThat(result.obligations().get(0).isEstimated()).isFalse();
    }

    @Test
    void insufficientOccurrencesYieldsDi() {
        List<CiBankTransaction> txns = List.of(
                emi(LocalDate.now().minusMonths(1), "45000", "HDFC BANK EMI"),
                emi(LocalDate.now().minusMonths(2), "45000", "HDFC BANK EMI"));
        var result = detector.detect(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                txns, 3, 0.7);
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.DATA_INSUFFICIENT.name());
    }

    private static CiBankTransaction emi(LocalDate date, String amt, String narr) {
        return CiBankTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .bankAccountId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .transactionDate(date)
                .amount(new BigDecimal(amt))
                .direction(TxnDirection.DEBIT.name())
                .category(TxnCategory.EMI.name())
                .emiFlag(true)
                .descriptionRaw(narr)
                .duplicateStatus("UNIQUE")
                .build();
    }
}
