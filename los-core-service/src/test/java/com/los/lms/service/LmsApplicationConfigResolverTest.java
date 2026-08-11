package com.los.lms.service;

import com.los.core.exception.BusinessRuleException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.empty());

        assertEquals("CUSTOM01", resolver.resolveEncoreProductCode(app));
        assertEquals(LmsProductMappingResolution.SOURCE_APPLICATION,
                resolver.requireEncoreProductMapping(app).mappingSource());
    }

    @Test
    void resolveEncoreProductCode_usesWorkflowConfig_notHardcoded() {
        UUID wfId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .id(wfId)
                .version(2)
                .lmsProductCode("IPPOPAYM01")
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.of(wf));

        LmsProductMappingResolution res = resolver.requireEncoreProductMapping(app);
        assertEquals("IPPOPAYM01", res.lmsProductCode());
        assertEquals(LmsProductMappingResolution.SOURCE_WORKFLOW, res.mappingSource());
        assertEquals(wfId, res.workflowId());
        assertEquals(2, res.workflowVersion());
    }

    @Test
    void resolveEncoreProductCode_honoursAlternativeConfiguredCode() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.PERSONAL_LOAN)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        WorkflowConfig wf = WorkflowConfig.builder()
                .lmsProductCode("ALT_PROD_99")
                .version(1)
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.of(wf));

        assertEquals("ALT_PROD_99", resolver.resolveEncoreProductCode(app));
    }

    @Test
    void resolveEncoreProductCode_missingMapping_failsClosed() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("LOS-TEST-1")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.PERSONAL_LOAN)
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> resolver.resolveEncoreProductCode(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING, ex.getReason());
        assertEquals("OPEN_LOAN_ACCOUNT", ex.getAction());
        assertTrue(ex.getMessage().contains("No LMS product mapping"));
    }

    @Test
    void resolveEncoreProductCode_blankWorkflowCode_failsClosed() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct(StandardLoanProduct.PERSONAL_LOAN)
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(app))
                .thenReturn(Optional.of(WorkflowConfig.builder().lmsProductCode("  ").build()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> resolver.resolveEncoreProductCode(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING, ex.getReason());
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
    void resolveEncoreProductCode_invoiceDiscountingWithoutProgramCode_failsClosed() {
        LoanApplication app = LoanApplication.builder()
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(app)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> resolver.resolveEncoreProductCode(app));
        assertEquals(LmsApplicationConfigResolver.REASON_LMS_PRODUCT_MAPPING_MISSING, ex.getReason());
    }

    @Test
    void productConfigPreview_matchesRuntime_whenBoundWorkflowConfigured() {
        UUID wfId = UUID.randomUUID();
        WorkflowConfig wf = WorkflowConfig.builder()
                .id(wfId)
                .version(2)
                .borrowerType("COMPANY")
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .lmsProductCode("IPPOPAYM01")
                .build();
        LoanApplication probe = LoanApplication.builder()
                .workflowId(wfId)
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.TERM_LOAN)
                .intakeSegment(IntakeSegment.BORROWER)
                .build();
        when(activeWorkflowConfigService.findActiveForApplication(probe)).thenReturn(Optional.of(wf));

        LmsProductMappingResolution runtime = resolver.requireEncoreProductMapping(probe);
        assertEquals(wf.getLmsProductCode(), runtime.lmsProductCode());
        assertEquals(wfId, runtime.workflowId());
        assertEquals(2, runtime.workflowVersion());
        assertFalse("HARDCODED".equals(runtime.mappingSource()));
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
