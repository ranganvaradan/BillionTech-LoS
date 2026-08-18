package com.los.core.creditintelligence;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationFreezeService;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationResolution;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalResolutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.canonicalshadow.CanonicalShadowUnderwritingService;
import com.los.core.creditintelligence.service.CreditIntelligenceFoundationService;
import com.los.core.creditintelligence.service.PolicyVersionResolver;
import com.los.core.creditintelligence.service.ShadowCreditEvaluationService;
import com.los.core.creditintelligence.service.UnderwritingFactSnapshotBuilder;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditIntelligenceFoundationServiceTest {

    @Mock
    private UnderwritingFactSnapshotBuilder snapshotBuilder;
    @Mock
    private PolicyVersionResolver policyVersionResolver;
    @Mock
    private ShadowCreditEvaluationService shadowCreditEvaluationService;
    @Mock
    private ExecutorService creditIntelligenceShadowExecutor;
    @Mock
    private com.los.core.creditintelligence.gst.service.GstIngestionService gstIngestionService;
    @Mock
    private com.los.core.creditintelligence.banking.service.BankingIngestionService bankingIngestionService;
    @Mock
    private com.los.core.creditintelligence.tax.service.TaxIngestionService taxIngestionService;
    @Mock
    private com.los.core.creditintelligence.policystudio.lifecycle.ShadowPolicyRoutingService shadowPolicyRoutingService;
    @Mock
    private CanonicalApplicationConfigurationFreezeService canonicalApplicationConfigurationFreezeService;
    @Mock
    private CanonicalShadowUnderwritingService canonicalShadowUnderwritingService;

    private CreditIntelligenceProperties properties;
    private CreditIntelligenceFoundationService service;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        service = new CreditIntelligenceFoundationService(
                properties, snapshotBuilder, policyVersionResolver,
                shadowCreditEvaluationService, creditIntelligenceShadowExecutor,
                gstIngestionService, bankingIngestionService, taxIngestionService,
                shadowPolicyRoutingService, canonicalApplicationConfigurationFreezeService,
                canonicalShadowUnderwritingService);
    }

    @Test
    void disabledFoundationDoesNothing() {
        properties.getFoundation().setEnabled(false);
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .build();
        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                700, true, null, null, null, null, "PROVIDER", "PROVIDER", "PROVIDER", Map.of());

        Optional<CreditIntelligenceFoundationService.PrepResult> prep =
                service.prepare(app, ctx, "PASS", "u1");

        assertTrue(prep.isEmpty());
        verify(snapshotBuilder, never()).buildAndFreeze(any(), any(), any(), any());
    }

    @Test
    void enabledPrepareCallsBuilderAndPolicyResolver() {
        properties.getFoundation().setEnabled(true);
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .build();
        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                700, true, new BigDecimal("50000"), null, null, null, "PROVIDER", "PROVIDER", "PROVIDER", Map.of());

        CiFactSnapshot snap = CiFactSnapshot.builder().id(UUID.randomUUID()).build();
        CiPolicyVersion pv = CiPolicyVersion.builder().id(UUID.randomUUID()).build();
        when(snapshotBuilder.buildAndFreeze(eq(app), eq(ctx), eq("PASS"), eq("u1")))
                .thenReturn(new UnderwritingFactSnapshotBuilder.FoundationPrep(snap, List.of()));
        when(policyVersionResolver.resolveAndFreeze(app, "u1")).thenReturn(pv);
        when(canonicalApplicationConfigurationFreezeService.freezeObservably(app))
                .thenReturn(new CanonicalApplicationConfigurationResolution(
                        CanonicalResolutionStatus.NOT_RESOLVABLE, null, List.of("CUSTOMER_CATEGORY_NOT_PINNED"), Map.of()));

        Optional<CreditIntelligenceFoundationService.PrepResult> prep =
                service.prepare(app, ctx, "PASS", "u1");

        assertTrue(prep.isPresent());
        verify(snapshotBuilder).buildAndFreeze(app, ctx, "PASS", "u1");
        verify(policyVersionResolver).resolveAndFreeze(app, "u1");
        verify(canonicalApplicationConfigurationFreezeService).freezeObservably(app);
    }

    @Test
    void canonicalFreezeFailureDoesNotAlterPrepareResult() {
        properties.getFoundation().setEnabled(true);
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .build();
        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                700, true, new BigDecimal("50000"), null, null, null, "PROVIDER", "PROVIDER", "PROVIDER", Map.of());
        CiFactSnapshot snap = CiFactSnapshot.builder().id(UUID.randomUUID()).build();
        CiPolicyVersion pv = CiPolicyVersion.builder().id(UUID.randomUUID()).build();
        when(snapshotBuilder.buildAndFreeze(eq(app), eq(ctx), eq("PASS"), eq("u1")))
                .thenReturn(new UnderwritingFactSnapshotBuilder.FoundationPrep(snap, List.of()));
        when(policyVersionResolver.resolveAndFreeze(app, "u1")).thenReturn(pv);
        doThrow(new IllegalStateException("canonical freeze boom"))
                .when(canonicalApplicationConfigurationFreezeService).freezeObservably(app);

        Optional<CreditIntelligenceFoundationService.PrepResult> prep =
                service.prepare(app, ctx, "PASS", "u1");

        assertTrue(prep.isPresent());
        verify(policyVersionResolver).resolveAndFreeze(app, "u1");
    }

    @Test
    void afterProductionDispatchesCanonicalShadowEvenWhenLegacyShadowDisabled() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .creditDecision("APPROVED")
                .build();
        UnderwritingEvaluation eval = UnderwritingEvaluation.builder().id(UUID.randomUUID()).build();
        MultiRuleEvalResult multi = new MultiRuleEvalResult(List.of(), "APPROVED", "APPROVED", 80, List.of());

        service.afterProduction(null, app, eval, multi, "APPROVED");

        verify(canonicalShadowUnderwritingService).afterLiveDecision(app, eval, multi, "APPROVED");
        verify(shadowCreditEvaluationService, never()).evaluate(any(), any(), any(), any(), any());
        assertTrue("APPROVED".equals(app.getCreditDecision()));
    }

    @Test
    void canonicalShadowFailureDoesNotPropagateFromAfterProduction() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .creditDecision("REJECTED")
                .build();
        doThrow(new IllegalStateException("canonical shadow boom"))
                .when(canonicalShadowUnderwritingService).afterLiveDecision(any(), any(), any(), any());

        service.afterProduction(null, app, null, null, "REJECTED");

        assertTrue("REJECTED".equals(app.getCreditDecision()));
    }
}
