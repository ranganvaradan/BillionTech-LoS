package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.BankAccountType;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.domain.OwnershipMatchStatus;
import com.los.core.creditintelligence.banking.service.CanonicalBankingRuleEvaluator;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalBankingRuleEvaluatorTest {

    @Test
    void mismatchAccountFailsOwnershipRule() {
        CiBankAccount mismatch = CiBankAccount.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .providerCode("AA")
                .accountType(BankAccountType.CURRENT.name())
                .holderNameMatchStatus(OwnershipMatchStatus.MISMATCH.name())
                .aggregationEligible(false)
                .parserVersion("V1")
                .normalizerVersion("V1")
                .build();
        var eval = new CanonicalBankingRuleEvaluator().evaluateOwnership(List.of(mismatch));
        assertThat(eval.outcome()).isEqualTo("FAIL");
    }

    @Test
    void dqAvailableWithAccounts() {
        CiBankAccount a = CiBankAccount.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .sourceRecordId(UUID.randomUUID())
                .providerCode("AA")
                .accountType(BankAccountType.SAVINGS.name())
                .parserVersion("V1")
                .normalizerVersion("V1")
                .build();
        assertThat(new CanonicalBankingRuleEvaluator().evaluateDqAvailable(List.of(a)).outcome())
                .isEqualTo("PASS");
        assertThat(new CanonicalBankingRuleEvaluator().evaluateDqAvailable(List.of()).outcome())
                .isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void chequeReturnValidZeroPasses() {
        CiMetricResult metric = CiMetricResult.builder()
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .metricCode("banking.cheque_return_count_3m")
                .metricVersion("V1")
                .outcome(BankingMetricOutcome.PASS.name())
                .value(Map.of("v", 0))
                .build();
        var eval = new CanonicalBankingRuleEvaluator().evaluateChequeReturn(
                Map.of("banking.cheque_return_count_3m", metric), 0);
        assertThat(eval.outcome()).isEqualTo("PASS");
    }
}
