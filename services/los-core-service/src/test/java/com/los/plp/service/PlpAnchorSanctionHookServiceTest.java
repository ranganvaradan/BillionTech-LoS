package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.config.PlpProperties;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlpAnchorSanctionHookServiceTest {

    @Mock
    private PlpProperties plpProperties;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private AnchorMasterRepository anchorMasterRepository;
    @Mock
    private PlpAnchorSyncService plpAnchorSyncService;

    @InjectMocks
    private PlpAnchorSanctionHookService hookService;

    @Test
    void skipsWhenPlpDisabled() {
        when(plpProperties.isEnabled()).thenReturn(false);

        hookService.onAnchorApplicationSanctioned(UUID.randomUUID());

        verifyNoInteractions(loanApplicationRepository);
    }

    @Test
    void skipsBorrowerIntake() {
        when(plpProperties.isEnabled()).thenReturn(true);
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .intakeSegment(IntakeSegment.BORROWER)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .build();
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));

        hookService.onAnchorApplicationSanctioned(appId);

        verify(anchorMasterRepository, never()).findBySourceAnchorApplicationId(any());
        verify(plpAnchorSyncService, never()).sync(any());
    }

    @Test
    void createsAnchorAndSyncsForInvoiceDiscountingAnchorIntake() {
        when(plpProperties.isEnabled()).thenReturn(true);
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("ANCH-2026-001")
                .intakeSegment(IntakeSegment.ANCHOR)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .businessInfo(Map.of(
                        "corporateName", "Acme Anchor Pvt Ltd",
                        "entityPan", "AAACB1234D",
                        "gstin", "27AAACB1234D1Z5",
                        "email", "ops@acme.example",
                        "mobile", "9876543210",
                        "addressLine", "1 MG Road",
                        "city", "Mumbai",
                        "state", "MH",
                        "pincode", "400001"))
                .build();
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(anchorMasterRepository.findBySourceAnchorApplicationId(appId)).thenReturn(Optional.empty());
        when(anchorMasterRepository.save(any())).thenAnswer(inv -> {
            AnchorMaster a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });

        hookService.onAnchorApplicationSanctioned(appId);

        ArgumentCaptor<AnchorMaster> captor = ArgumentCaptor.forClass(AnchorMaster.class);
        verify(anchorMasterRepository).save(captor.capture());
        AnchorMaster saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Acme Anchor Pvt Ltd");
        assertThat(saved.getCode()).isEqualTo("ANCH-2026-001");
        assertThat(saved.getSourceAnchorApplicationId()).isEqualTo(appId);
        assertThat(saved.getPlpAnchorSyncStatus()).isEqualTo(PlpSyncStatus.NOT_SYNCED);

        verify(plpAnchorSyncService).sync(saved.getId());
    }

    @Test
    void creditRatingCompletedCreatesAnchorAndSyncs() {
        when(plpProperties.isEnabled()).thenReturn(true);
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("LOS-ANC-20260520-16394")
                .intakeSegment(IntakeSegment.ANCHOR)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .businessInfo(Map.of("corporateName", "Test Anchor Corp"))
                .build();
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(anchorMasterRepository.findBySourceAnchorApplicationId(appId)).thenReturn(Optional.empty());
        when(anchorMasterRepository.save(any())).thenAnswer(inv -> {
            AnchorMaster a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });

        hookService.onAnchorCreditRatingCompleted(appId);

        verify(plpAnchorSyncService).sync(any(UUID.class));
    }

    @Test
    void camReviewedEntryPointStillSyncsForLegacyFlow() {
        when(plpProperties.isEnabled()).thenReturn(true);
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("LOS-ANC-20260520-16394")
                .intakeSegment(IntakeSegment.ANCHOR)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .businessInfo(Map.of("corporateName", "Test Anchor Corp"))
                .build();
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(anchorMasterRepository.findBySourceAnchorApplicationId(appId)).thenReturn(Optional.empty());
        when(anchorMasterRepository.save(any())).thenAnswer(inv -> {
            AnchorMaster a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });

        hookService.onAnchorApplicationCamReviewed(appId);

        verify(plpAnchorSyncService).sync(any(UUID.class));
    }
}
