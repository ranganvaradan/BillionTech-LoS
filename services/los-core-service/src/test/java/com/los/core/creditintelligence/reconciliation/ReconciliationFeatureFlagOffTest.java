package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.repository.CiCreditEvidenceSummaryRepository;
import com.los.core.creditintelligence.reconciliation.repository.CiReconciliationResultRepository;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationIngestionService;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationOrchestrator;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature flag off = no reconciliation behavioural change (smoke).
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationFeatureFlagOffTest {

    @Mock LoanApplicationRepository loanApplicationRepository;
    @Mock ReconciliationOrchestrator orchestrator;
    @Mock CiReconciliationResultRepository resultRepository;
    @Mock CiCreditEvidenceSummaryRepository evidenceSummaryRepository;

    private ReconciliationIngestionService ingestionService;
    private CreditIntelligenceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        properties.getReconciliation().setEnabled(false);
        ingestionService = new ReconciliationIngestionService(
                properties, loanApplicationRepository, orchestrator,
                resultRepository, evidenceSummaryRepository);
    }

    @Test
    void flagOffIsEnabledForReturnsFalse() {
        LoanApplication app = new LoanApplication();
        app.setId(UUID.randomUUID());
        app.setLoanProduct("SCF");
        assertThat(ingestionService.isEnabledFor(app)).isFalse();
    }

    @Test
    void flagOffEnsureRanDoesNothing() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));

        var result = ingestionService.ensureRan(appId, null, null, null);
        assertThat(result).isEmpty();
        verify(orchestrator, never()).run(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void flagOffRunForShadowEmpty() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        assertThat(ingestionService.runForShadow(appId, null, null)).isEmpty();
    }
}
