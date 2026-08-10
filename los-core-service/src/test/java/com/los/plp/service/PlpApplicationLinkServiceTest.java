package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.model.dto.LinkProgramRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlpApplicationLinkServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    private ProgramMasterRepository programMasterRepository;
    @Mock
    private AnchorMasterRepository anchorMasterRepository;

    @InjectMocks
    private PlpApplicationLinkService linkService;

    @Test
    void linkProgram_setsSubProgramIdWhenSynced() {
        UUID appId = UUID.randomUUID();
        UUID subProgramId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();

        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .build();

        SubProgramMaster sp = SubProgramMaster.builder()
                .id(subProgramId)
                .programId(programId)
                .anchorId(anchorId)
                .plpSubProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .build();
        ProgramMaster program = ProgramMaster.builder()
                .id(programId)
                .plpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .build();
        AnchorMaster anchor = AnchorMaster.builder()
                .id(anchorId)
                .plpAnchorSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .build();

        LinkProgramRequest request = new LinkProgramRequest();
        request.setSubProgramId(subProgramId);

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(subProgramMasterRepository.findById(subProgramId)).thenReturn(Optional.of(sp));
        when(programMasterRepository.findById(programId)).thenReturn(Optional.of(program));
        when(anchorMasterRepository.findById(anchorId)).thenReturn(Optional.of(anchor));
        when(loanApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanApplication result = linkService.linkProgram(appId, request);

        assertThat(result.getSubProgramId()).isEqualTo(subProgramId);
        verify(loanApplicationRepository).save(app);
    }

    @Test
    void linkProgram_rejectsNonInvoiceDiscounting() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .loanProduct("PERSONAL_LOAN")
                .build();
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));

        LinkProgramRequest request = new LinkProgramRequest();
        request.setSubProgramId(UUID.randomUUID());

        assertThatThrownBy(() -> linkService.linkProgram(appId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUSINESS_WC_INVOICE_DISCOUNTING");
    }
}
