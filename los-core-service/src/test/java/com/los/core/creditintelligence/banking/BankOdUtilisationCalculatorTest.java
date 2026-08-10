package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.BankAccountType;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.BankingMismatchClassification;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.service.BankOdUtilisationCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankOdUtilisationCalculatorTest {

    @Test
    void missingLimitYieldsDataInsufficient() {
        CiBankAccount od = CiBankAccount.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .providerCode("AA")
                .accountType(BankAccountType.OVERDRAFT.name())
                .parserVersion("V1")
                .normalizerVersion("V1")
                .build();
        var result = new BankOdUtilisationCalculator().calculate(od, List.of(), 6, null, 90);
        assertThat(result.outcome()).isEqualTo(BankingMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(result.evidence().get("reason"))
                .isEqualTo(BankingMismatchClassification.OD_LIMIT_MISSING.name());
    }
}
