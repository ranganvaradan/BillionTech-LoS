package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlpSanctionSyncOrchestratorTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    private PlpIntegrationClient plpIntegrationClient;
    @Mock
    private PlpSubProgramSyncService plpSubProgramSyncService;
    @Mock
    private PlpBorrowerSyncService plpBorrowerSyncService;
    @Mock
    private PlpBorrowerLinkSyncService plpBorrowerLinkSyncService;
    @Mock
    private PlpBorrowerProgramMappingSyncService plpBorrowerProgramMappingSyncService;

    @InjectMocks
    private PlpSanctionSyncOrchestrator orchestrator;

    @Test
    void syncAfterSanction_skipsWhenDisabled() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = borrowerApp(appId);
        when(plpIntegrationClient.isEnabled()).thenReturn(false);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));

        LoanApplication result = orchestrator.syncAfterSanction(appId);

        assertThat(result).isSameAs(app);
        verifyNoInteractions(plpBorrowerSyncService);
    }

    @Test
    void syncAfterSanction_skipsNonInvoiceDiscountingProduct() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = borrowerApp(appId);
        app.setLoanProduct("PERSONAL_LOAN");
        when(plpIntegrationClient.isEnabled()).thenReturn(true);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));

        LoanApplication result = orchestrator.syncAfterSanction(appId);

        assertThat(result).isSameAs(app);
        verifyNoInteractions(plpBorrowerSyncService);
    }

    @Test
    void syncAfterSanction_failsWhenSubProgramMissing() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = borrowerApp(appId);
        app.setSubProgramId(null);
        when(plpIntegrationClient.isEnabled()).thenReturn(true);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.syncAfterSanction(appId);

        assertThat(app.getPlpProgramSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_FAILED);
        assertThat(app.getPlpProgramSyncError()).contains("sub_program_id");
    }

    @Test
    void syncAfterSanction_resumesFromBorrowerStep() {
        UUID appId = UUID.randomUUID();
        UUID subProgramId = UUID.randomUUID();
        LoanApplication app = borrowerApp(appId);
        app.setSubProgramId(subProgramId);
        app.setPlpBorrowerId(UUID.randomUUID());
        app.setPlpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS);

        SubProgramMaster sp = SubProgramMaster.builder()
                .id(subProgramId)
                .plpSubProgramId(UUID.randomUUID())
                .plpSubProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .build();

        when(plpIntegrationClient.isEnabled()).thenReturn(true);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subProgramMasterRepository.findById(subProgramId)).thenReturn(Optional.of(sp));
        when(plpBorrowerLinkSyncService.syncLinkStep(any())).thenAnswer(inv -> {
            LoanApplication a = inv.getArgument(0);
            a.setPlpSubProgramBorrowerId(UUID.randomUUID());
            return a;
        });
        when(plpBorrowerProgramMappingSyncService.syncMappingStep(any())).thenAnswer(inv -> {
            LoanApplication a = inv.getArgument(0);
            a.setPlpBorrowerProgramMappingId(UUID.randomUUID());
            a.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            return a;
        });

        orchestrator.syncAfterSanction(appId);

        verify(plpBorrowerSyncService, never()).syncBorrowerStep(any());
        verify(plpBorrowerLinkSyncService).syncLinkStep(any());
        verify(plpBorrowerProgramMappingSyncService).syncMappingStep(any());
    }

    @Test
    void syncAfterSanction_reconcilesBorrowerStatusWhenIdExistsButNotSynced() {
        UUID appId = UUID.randomUUID();
        UUID subProgramId = UUID.randomUUID();
        LoanApplication app = borrowerApp(appId);
        app.setSubProgramId(subProgramId);
        app.setPlpBorrowerId(UUID.randomUUID());
        app.setPlpSubProgramBorrowerId(UUID.randomUUID());
        app.setPlpBorrowerProgramMappingId(UUID.randomUUID());
        app.setPlpBorrowerSyncStatus(PlpSyncStatus.NOT_SYNCED);
        app.setPlpLinkSyncStatus(PlpSyncStatus.NOT_SYNCED);
        app.setPlpMappingSyncStatus(PlpSyncStatus.NOT_SYNCED);

        SubProgramMaster sp = SubProgramMaster.builder()
                .id(subProgramId)
                .plpSubProgramId(UUID.randomUUID())
                .plpSubProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .build();

        when(plpIntegrationClient.isEnabled()).thenReturn(true);
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subProgramMasterRepository.findById(subProgramId)).thenReturn(Optional.of(sp));

        LoanApplication result = orchestrator.syncAfterSanction(appId);

        assertThat(result.getPlpBorrowerSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(result.getPlpLinkSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(result.getPlpMappingSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        verify(plpBorrowerSyncService, never()).syncBorrowerStep(any());
        verify(plpBorrowerLinkSyncService, never()).syncLinkStep(any());
        verify(plpBorrowerProgramMappingSyncService, never()).syncMappingStep(any());
        verify(loanApplicationRepository, atLeastOnce()).save(any());
    }

    private static LoanApplication borrowerApp(UUID id) {
        return LoanApplication.builder()
                .id(id)
                .customerId(UUID.randomUUID())
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
    }
}
