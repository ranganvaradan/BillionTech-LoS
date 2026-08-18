package com.los.core.service.workflow;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W1 goldens — one application → one resolved Workflow Version.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationWorkflowResolverW1GoldensTest {

    @Mock LoanApplicationRepository loanApplicationRepository;
    @Mock WorkflowConfigRepository workflowConfigRepository;
    @Mock AuditService auditService;

    ApplicationWorkflowResolver resolver;
    ActiveWorkflowConfigService activeWorkflowConfigService;

    UUID appId;
    UUID wfV1Id;
    UUID wfV2Id;
    WorkflowConfig wfV1;
    WorkflowConfig wfV2;
    LoanApplication app;

    @BeforeEach
    void setUp() {
        resolver = new ApplicationWorkflowResolver(loanApplicationRepository, workflowConfigRepository, auditService);
        activeWorkflowConfigService = new ActiveWorkflowConfigService(resolver);
        appId = UUID.randomUUID();
        wfV1Id = UUID.randomUUID();
        wfV2Id = UUID.randomUUID();
        wfV1 = WorkflowConfig.builder()
                .id(wfV1Id)
                .name("Journey V1")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .active(true)
                .version(1)
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true)))
                .build();
        wfV2 = WorkflowConfig.builder()
                .id(wfV2Id)
                .name("Journey V2")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .active(true)
                .version(2)
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true), Map.of("step", "AADHAAR_OTP", "mandatory", true)))
                .build();
        app = LoanApplication.builder()
                .id(appId)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void goldenA_unconfiguredApplicationDoesNotDiscoverDefault() {
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        "INDIVIDUAL", "PERSONAL_LOAN", "BORROWER"))
                .thenReturn(List.of(wfV2, wfV1));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> resolver.resolveForApplication(app));
        assertEquals("WORKFLOW_NOT_PINNED", ex.getReason());
        assertEquals(null, app.getWorkflowId());
        verify(loanApplicationRepository, never()).save(any());
        verify(workflowConfigRepository, never())
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(any(), any(), any());
    }

    @Test
    void goldenB_productConfigChange_keepsPersistedWorkflow() {
        app.setWorkflowId(wfV1Id);
        app.setWorkflowVersion(1);
        app.setWorkflowResolutionSource(WorkflowResolutionSource.EXPLICIT.name());
        app.setWorkflowResolvedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        app.setWorkflowContentHash(WorkflowContentHash.of(wfV1));
        when(workflowConfigRepository.findById(wfV1Id)).thenReturn(Optional.of(wfV1));

        // Newer active head exists
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        any(), any(), any()))
                .thenReturn(List.of(wfV2, wfV1));

        ResolvedWorkflowVersion resolved = resolver.resolveForApplication(app);
        assertEquals(wfV1Id, resolved.workflowId());
        verify(workflowConfigRepository, never())
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(any(), any(), any());
    }

    @Test
    void goldenC_kycPathUsesSameResolverAsActiveService() {
        app.setWorkflowId(wfV1Id);
        app.setWorkflowVersion(1);
        app.setWorkflowResolutionSource(WorkflowResolutionSource.EXPLICIT.name());
        app.setWorkflowResolvedAt(java.time.Instant.now());
        app.setWorkflowContentHash(WorkflowContentHash.of(wfV1));
        when(workflowConfigRepository.findById(wfV1Id)).thenReturn(Optional.of(wfV1));

        WorkflowConfig viaResolver = resolver.requireConfig(app);
        WorkflowConfig viaActive = activeWorkflowConfigService.findActiveForApplication(app).orElseThrow();
        assertEquals(viaResolver.getId(), viaActive.getId());
    }

    @Test
    void goldenE_noActiveReResolutionAfterPersist() {
        app.setWorkflowId(wfV1Id);
        app.setWorkflowVersion(1);
        app.setWorkflowResolutionSource(WorkflowResolutionSource.DEFAULT.name());
        app.setWorkflowResolvedAt(java.time.Instant.now());
        app.setWorkflowContentHash(WorkflowContentHash.of(wfV1));
        when(workflowConfigRepository.findById(wfV1Id)).thenReturn(Optional.of(wfV1));

        resolver.resolveForApplication(app);
        resolver.resolveForApplication(app);
        verify(workflowConfigRepository, never())
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(any(), any(), any());
        verify(workflowConfigRepository, atLeastOnce()).findById(wfV1Id);
    }

    @Test
    void goldenF_missingPersistedWorkflowFailsClosed() {
        UUID missing = UUID.randomUUID();
        app.setWorkflowId(missing);
        when(workflowConfigRepository.findById(missing)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> resolver.resolveForApplication(app));
        assertEquals("WORKFLOW_VERSION_NOT_FOUND", ex.getReason());
        // Must not fall back to active head
        verify(workflowConfigRepository, never())
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(any(), any(), any());
    }

    @Test
    void goldenG_legacyWorkflowIdPreserved() {
        app.setWorkflowId(wfV1Id);
        // no meta yet — LEGACY_EXISTING backfill
        when(workflowConfigRepository.findById(wfV1Id)).thenReturn(Optional.of(wfV1));

        ResolvedWorkflowVersion r = resolver.resolveForApplication(app);
        assertEquals(wfV1Id, r.workflowId());
        assertEquals(WorkflowResolutionSource.LEGACY_EXISTING, r.resolutionSource());
        assertEquals(wfV1Id, app.getWorkflowId());
        verify(loanApplicationRepository, never()).save(any());
    }

    @Test
    void goldenH_newApplicationDoesNotResolveLatestActive() {
        LoanApplication newApp = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        "INDIVIDUAL", "PERSONAL_LOAN", "BORROWER"))
                .thenReturn(List.of(wfV2, wfV1));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> resolver.resolveForApplication(newApp));
        assertEquals("WORKFLOW_NOT_PINNED", ex.getReason());
        assertEquals(null, newApp.getWorkflowId());
        verify(loanApplicationRepository, never()).save(any());
    }

    @Test
    void goldenF2_unresolvableFailsClosed() {
        LoanApplication bare = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("UNKNOWN_PRODUCT")
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
                        any(), any(), any()))
                .thenReturn(List.of());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> resolver.resolveForApplication(bare));
        assertEquals("WORKFLOW_NOT_PINNED", ex.getReason());
        verify(loanApplicationRepository, never()).save(any());
    }

    @Test
    void brokenReference_activeServiceDoesNotSilentFallback() {
        UUID missing = UUID.randomUUID();
        app.setWorkflowId(missing);
        when(workflowConfigRepository.findById(missing)).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> activeWorkflowConfigService.findActiveForApplication(app));
    }

    @Test
    void contentHashDetectsMutation() {
        String hash = WorkflowContentHash.of(wfV1);
        app.setWorkflowId(wfV1Id);
        app.setWorkflowVersion(1);
        app.setWorkflowResolutionSource(WorkflowResolutionSource.EXPLICIT.name());
        app.setWorkflowResolvedAt(java.time.Instant.now());
        app.setWorkflowContentHash(hash);

        WorkflowConfig mutated = WorkflowConfig.builder()
                .id(wfV1Id)
                .name("Journey V1")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .active(true)
                .version(1)
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true), Map.of("step", "FACE_MATCH", "mandatory", true)))
                .build();
        when(workflowConfigRepository.findById(wfV1Id)).thenReturn(Optional.of(mutated));

        ResolvedWorkflowVersion r = resolver.resolveForApplication(app);
        assertTrue(r.definitionMutatedSinceResolve());
        assertFalse(hash.equals(WorkflowContentHash.of(mutated)));
        // Still same workflow id — no silent substitute
        assertEquals(wfV1Id, r.workflowId());
    }

    @Test
    void stampExplicit_setsMetadata() {
        ArgumentCaptor<LoanApplication> cap = ArgumentCaptor.forClass(LoanApplication.class);
        resolver.stampExplicit(app, wfV1);
        verify(loanApplicationRepository).save(cap.capture());
        LoanApplication saved = cap.getValue();
        assertEquals(wfV1Id, saved.getWorkflowId());
        assertEquals(WorkflowResolutionSource.EXPLICIT.name(), saved.getWorkflowResolutionSource());
        assertEquals(1, saved.getWorkflowVersion());
    }
}
