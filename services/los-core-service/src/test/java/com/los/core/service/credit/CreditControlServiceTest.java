package com.los.core.service.credit;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.kyc.IKycOrchestrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CreditControlServiceTest {

    @Mock
    private IKycOrchestrationService kyc;

    @Test
    void resolveEffective_usesProviderBureauByDefault() {
        CreditControlService svc = new CreditControlService(kyc);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(700)
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.effectiveBureauScore()).isEqualTo(700);
        assertThat(ctx.kycPassEffective()).isTrue();
    }

    @Test
    void resolveEffective_manualBureauSource_prefersEntityOverride() {
        CreditControlService svc = new CreditControlService(kyc);
        Map<String, Object> fi = new HashMap<>();
        Map<String, Object> cc = new HashMap<>();
        Map<String, Object> ds = new HashMap<>();
        ds.put("bureauScoreSource", "MANUAL");
        ds.put("kycSource", "PROVIDER");
        cc.put("decisionSources", ds);
        fi.put("creditControl", cc);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(600)
                .manualBureauScore(750)
                .financialInfo(fi)
                .build();
        assertThat(svc.resolveEffective(app, "PASS").effectiveBureauScore()).isEqualTo(750);
    }

    @Test
    void resolveEffective_scorecard_includesBureauKey() {
        CreditControlService svc = new CreditControlService(kyc);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(720)
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard().get("BUREAU_SCORE").intValue()).isEqualTo(720);
    }
}
