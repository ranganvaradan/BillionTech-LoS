package com.los.lms.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.los.encore.client.api.EncoreTemporaryOverrides.DEFAULT_ENCORE_PRODUCT_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LmsApplicationConfigResolverTest {

    @Mock
    private ActiveWorkflowConfigService activeWorkflowConfigService;
    @Mock
    private SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    private ProgramMasterRepository programMasterRepository;

    @InjectMocks
    private LmsApplicationConfigResolver resolver;

    @Test
    void resolveEncoreProductCode_prefersApplicationValue() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.PERSONAL_LOAN)
                .lmsProductCode("CUSTOM01")
                .build();

        assertEquals("CUSTOM01", resolver.resolveEncoreProductCode(app));
    }

    @Test
    void resolveEncoreProductCode_fallsBackToWorkflowDefault() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.PERSONAL_LOAN)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .lmsProductCode("WFPROD01")
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.of(wf));

        assertEquals("WFPROD01", resolver.resolveEncoreProductCode(app));
    }

    @Test
    void resolveEncoreProductCode_usesHardcodedFallbackWhenNothingSet() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.PERSONAL_LOAN)
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.empty());

        assertEquals(DEFAULT_ENCORE_PRODUCT_CODE, resolver.resolveEncoreProductCode(app));
    }

    @Test
    void resolveEncoreProductCode_invoiceDiscountingProgramBypassesApplicationField() {
        UUID subProgramId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .subProgramId(subProgramId)
                .lmsProductCode("SHOULD_NOT_USE")
                .build();
        SubProgramMaster sub = SubProgramMaster.builder().programId(programId).build();
        ProgramMaster program = ProgramMaster.builder()
                .productType("INVOICE_DISCOUNTING")
                .encoreProductCode("ID_PROG_CODE")
                .build();
        when(subProgramMasterRepository.findById(subProgramId)).thenReturn(Optional.of(sub));
        when(programMasterRepository.findById(programId)).thenReturn(Optional.of(program));

        assertEquals("ID_PROG_CODE", resolver.resolveEncoreProductCode(app));
    }

    @Test
    void resolveTenureUnit_prefersApplicationAndNormalizesDay() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .lmsTenureUnit("DAY")
                .build();

        assertEquals("Day", resolver.resolveTenureUnit(app));
    }

    @Test
    void resolveTenureUnit_fallsBackToWorkflowDefault() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .build();
        WorkflowConfig wf = WorkflowConfig.builder().lmsTenureUnit("Week").build();
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.of(wf));

        assertEquals("Week", resolver.resolveTenureUnit(app));
    }

    @Test
    void normalizeTenureUnit_mapsMonthAlias() {
        assertEquals("Month", resolver.normalizeTenureUnit("MONTH"));
    }
}
