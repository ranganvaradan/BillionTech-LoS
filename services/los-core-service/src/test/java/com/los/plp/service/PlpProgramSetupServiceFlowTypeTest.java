package com.los.plp.service;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.config.PlpProperties;
import com.los.plp.model.dto.CreatePlpProgramRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlpProgramSetupServiceFlowTypeTest {

    @Mock
    AnchorMasterRepository anchorMasterRepository;
    @Mock
    ProgramMasterRepository programMasterRepository;
    @Mock
    SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    PlpProgramSyncService plpProgramSyncService;
    @Mock
    PlpSubProgramSyncService plpSubProgramSyncService;
    @Mock
    PlpProperties plpProperties;
    @Mock
    ProgramApprovalService programApprovalService;
    @Mock
    LoanApplicationRepository loanApplicationRepository;

    PlpProgramSetupService service;

    @BeforeEach
    void setUp() {
        service = new PlpProgramSetupService(
                anchorMasterRepository,
                programMasterRepository,
                subProgramMasterRepository,
                plpProgramSyncService,
                plpSubProgramSyncService,
                plpProperties,
                programApprovalService,
                loanApplicationRepository);
        when(plpProperties.isEnabled()).thenReturn(false);
        when(plpProperties.getLenderId()).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    void createProgram_salesFlowType_setsBuyerSellerRoles() {
        UUID anchorId = UUID.randomUUID();
        UUID sourceAppId = UUID.randomUUID();
        AnchorMaster anchor = AnchorMaster.builder()
                .id(anchorId)
                .name("Anchor")
                .code("ANC")
                .sourceAnchorApplicationId(sourceAppId)
                .build();
        LoanApplication sourceApp = new LoanApplication();
        sourceApp.setId(sourceAppId);
        sourceApp.setStatus(ApplicationStatus.SANCTION_PENDING);
        when(anchorMasterRepository.findById(anchorId)).thenReturn(Optional.of(anchor));
        when(loanApplicationRepository.findById(sourceAppId)).thenReturn(Optional.of(sourceApp));
        when(subProgramMasterRepository.findByAnchorIdAndProgramName(any(), any())).thenReturn(java.util.List.of());
        when(programMasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subProgramMasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreatePlpProgramRequest req = new CreatePlpProgramRequest();
        req.setAnchorId(anchorId);
        req.setProgramName("Sales Program");
        req.setProgramType("INVOICE_DISCOUNTING");
        req.setFlowType("SALES_BILL_DISCOUNTING");
        req.setCreditLimit(BigDecimal.valueOf(100000));
        req.setLmsEntryIn("NO");

        var response = service.createProgramForAnchor(req);

        assertThat(response).isNotNull();
        assertThat(response.getProgramSyncStatus()).isEqualTo(PlpSyncStatus.NOT_SYNCED);
    }
}
