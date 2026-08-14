package com.los.core.service.credit;

import com.los.core.creditintelligence.bureau.service.CanonicalBureauContextBridge;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.document.OcrExtractionService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.plp.service.InvoiceDiscountingVintageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditControlServiceTest {

    @Mock
    private IKycOrchestrationService kyc;

    @Mock
    private InvoiceDiscountingVintageService invoiceDiscountingVintageService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private KycStepResultRepository kycStepResultRepository;

    @Mock
    private OcrExtractionService ocrExtractionService;

    @Mock
    private LimitSizingService limitSizingService;

    @Mock
    private CanonicalBureauContextBridge canonicalBureauContextBridge;

    private CreditControlService service() {
        return service(true);
    }

    private CreditControlService service(boolean providerGapDefaultsEnabled) {
        lenient().when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        lenient().when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(java.util.Optional.empty());
        lenient().when(canonicalBureauContextBridge.isEnabledFor(any())).thenReturn(false);
        CreditControlService svc = new CreditControlService(
                kyc,
                invoiceDiscountingVintageService,
                documentRepository,
                kycStepResultRepository,
                ocrExtractionService,
                limitSizingService,
                canonicalBureauContextBridge);
        ReflectionTestUtils.setField(svc, "providerGapDefaultsEnabled", providerGapDefaultsEnabled);
        return svc;
    }

    @Test
    void resolveEffective_usesProviderBureauByDefault() {
        CreditControlService svc = service();
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
        CreditControlService svc = service();
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
        CreditControlService svc = service();
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

    @Test
    void resolveEffective_doesNotApplyDemoFallbackWithoutExplicitDemoFlag() {
        CreditControlService svc = service();
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(0)
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "FAIL");
        assertThat(ctx.effectiveBureauScore()).isZero();
        assertThat(ctx.kycPassEffective()).isFalse();
        assertThat(ctx.scorecard()).doesNotContainKey("DEMO_FALLBACK_ACTIVE");
    }

    @Test
    void resolveEffective_appliesDemoFallbackWhenExplicitDemoFlag() {
        CreditControlService svc = service();
        Map<String, Object> fi = new HashMap<>();
        fi.put("demo", true);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(0)
                .financialInfo(fi)
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "INCOMPLETE");
        assertThat(ctx.effectiveBureauScore()).isEqualTo(720);
        assertThat(ctx.kycPassEffective()).isTrue();
        assertThat(ctx.scorecard()).containsKey("DEMO_FALLBACK_ACTIVE");
    }

    @Test
    void resolveEffective_usesDeclaredMonthlyNetIncomeFromPersonalInfo() {
        CreditControlService svc = service();
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(700)
                .personalInfo(Map.of("monthlyNetIncome", "75000"))
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard().get("MONTHLY_INCOME").intValue()).isEqualTo(75000);
        assertThat(ctx.effectiveIncome().intValue()).isEqualTo(75000);
    }

    @Test
    void resolveEffective_appliesGapDefaultsForBankDerivedScorecardFields() {
        CreditControlService svc = service(true);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(700)
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard()).containsKey("MONTHLY_INCOME");
        assertThat(ctx.scorecard()).containsKey("OBLIGATION_RATIO");
        assertThat(ctx.scorecard()).containsKey("AVERAGE_BANK_BALANCE");
        assertThat(ctx.scorecard().get("PROVIDER_GAP_DEFAULT_ACTIVE").intValue()).isEqualTo(1);
        assertThat(ctx.scorecard().get("MONTHLY_INCOME").intValue()).isEqualTo(85000);
        assertThat(ctx.scorecard().get("AVERAGE_BANK_BALANCE").intValue()).isEqualTo(120000);
        // BUREAU-P0-2: gap flag must not invent Bureau decision inputs
        assertThat(ctx.scorecard()).doesNotContainKey("LIVE_UNSECURED_LOAN_COUNT");
        assertThat(ctx.scorecard()).doesNotContainKey("BUREAU_ENQUIRIES_3M");
        assertThat(ctx.scorecard()).doesNotContainKey("NTC_FLAG");
    }

    @Test
    void resolveEffective_skipsInventedBankGapsWhenProviderDefaultsDisabled() {
        CreditControlService svc = service(false);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(700)
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard()).doesNotContainKey("AVERAGE_BANK_BALANCE");
        assertThat(ctx.scorecard()).doesNotContainKey("avgDailyBalance3m");
        assertThat(ctx.scorecard()).doesNotContainKey("ANNUAL_GST_TURNOVER");
    }

    @Test
    void resolveEffective_gapDefaultsDoNotOverrideManualBankBalance() {
        CreditControlService svc = service(true);
        Map<String, Object> fi = new HashMap<>();
        Map<String, Object> cc = new HashMap<>();
        Map<String, Object> manual = new HashMap<>();
        manual.put("averageBankBalance", Map.of("value", "45000", "source", "MANUAL"));
        cc.put("manual", manual);
        fi.put("creditControl", cc);
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(700)
                .financialInfo(fi)
                .build();
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard().get("AVERAGE_BANK_BALANCE").intValue()).isEqualTo(45000);
        assertThat(ctx.scorecard().get("MONTHLY_INCOME").intValue()).isEqualTo(85000);
    }

    @Test
    void applyManualKycPassOnProcessOverride_usesManualKycSource() {
        CreditControlService svc = service();
        LoanApplication app = LoanApplication.builder()
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("P")
                .bureauScore(700)
                .build();
        svc.applyManualKycPassOnProcessOverride(app, "Ops approved after document review");
        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "FAIL");
        assertThat(ctx.kycPassEffective()).isTrue();
        assertThat(ctx.kycSource()).isEqualTo("MANUAL");
    }
}
