package com.los.core.service.loan;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.UpdateApplicationRequest;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.CreditAppraisalMemoRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.audit.RecordAuditService;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.loan.intake.ApplicationCustomerIdResolver;
import com.los.core.service.loan.intake.ApplicationSubmitIdentityValidator;
import com.los.core.service.loan.intake.IntakeMetadataEnricher;
import com.los.core.service.underwriting.UnderwritingEvaluationService;
import com.los.core.service.workflow.ApplicationConfigurationAuthority;
import com.los.core.service.workflow.ApplicationWorkflowResolver;
import com.los.core.service.workflow.WorkflowResolutionSource;
import com.los.lms.repository.ExternalProductMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Reproduces the reported bug: a Category-governed application's multi-step intake wizard
 * resends the full known form state on every "Continue" (including fields already pinned by
 * the canonical external product mapping / category selection). Resending the SAME value must
 * be a silent no-op — only an actual attempted CHANGE should trip the fail-closed guards in
 * {@link ApplicationConfigurationAuthority}.
 */
@ExtendWith(MockitoExtension.class)
class LoanApplicationCategoryGovernedIdempotentUpdateTest {

    @Mock LoanApplicationRepository applicationRepository;
    @Mock AuditService auditService;
    @Mock RecordAuditService recordAuditService;
    @Mock CreditControlService creditControlService;
    @Mock UnderwritingEvaluationService underwritingEvaluationService;
    @Mock CreditAppraisalMemoRepository creditAppraisalMemoRepository;
    @Mock ApplicationCustomerIdResolver applicationCustomerIdResolver;
    @Mock IntakeMetadataEnricher intakeMetadataEnricher;
    @Mock ApplicationSubmitIdentityValidator applicationSubmitIdentityValidator;
    @Mock ApplicationInputChangeTracker applicationInputChangeTracker;
    @Mock WorkflowConfigRepository workflowConfigRepository;
    @Mock ApplicationWorkflowResolver applicationWorkflowResolver;
    @Mock CustomerCategoryRepository customerCategoryRepository;
    @Mock ExternalProductMappingRepository externalProductMappingRepository;

    LoanApplicationServiceImpl service;
    UUID appId = UUID.randomUUID();
    UUID workflowId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LoanApplicationServiceImpl(
                applicationRepository, auditService, recordAuditService, creditControlService,
                underwritingEvaluationService, creditAppraisalMemoRepository, applicationCustomerIdResolver,
                intakeMetadataEnricher, applicationSubmitIdentityValidator, applicationInputChangeTracker,
                workflowConfigRepository, applicationWorkflowResolver, customerCategoryRepository,
                externalProductMappingRepository);

        lenient().when(applicationInputChangeTracker.fullFingerprint(any())).thenReturn(Map.of());
        lenient().when(applicationInputChangeTracker.diffSnapshots(any(), any())).thenReturn(List.of());
        lenient().when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(workflowConfigRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(customerCategoryRepository.findById(any())).thenReturn(Optional.empty());
    }

    private LoanApplication categoryGovernedApp() {
        return LoanApplication.builder()
                .id(appId)
                .applicationNumber("LOS-TEST-1")
                .loanProduct("BUSINESS_TERM_LOAN")
                .status(ApplicationStatus.DRAFT)
                .workflowId(workflowId)
                .selectedCustomerCategoryId(categoryId)
                .workflowResolutionSource(WorkflowResolutionSource.CATEGORY_SELECTION.name())
                .lmsTenureUnit("MONTHLY")
                .lmsProductCode("EXISTING_CODE")
                .creditVintage("EXISTING_CUSTOMER")
                .build();
    }

    @Test
    void resendingSameLmsTenureUnit_onCategoryGovernedApp_isNoOp() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(categoryGovernedApp()));
        UpdateApplicationRequest req = new UpdateApplicationRequest();
        req.setLmsTenureUnit("MONTHLY"); // same value the wizard already knows about

        var response = service.updateApplication(appId, req);
        assertThat(response).isNotNull();
    }

    @Test
    void changingLmsTenureUnit_onCategoryGovernedApp_stillBlocked() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(categoryGovernedApp()));
        UpdateApplicationRequest req = new UpdateApplicationRequest();
        req.setLmsTenureUnit("WEEKLY"); // genuine attempted change

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateApplication(appId, req));
        assertThat(ex.getReason())
                .isEqualTo(ApplicationConfigurationAuthority.LMS_FIELD_NOT_PERMITTED_ON_CATEGORY_GOVERNED_APP);
    }

    @Test
    void resendingSameLmsProductCode_onCategoryGovernedApp_isNoOp() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(categoryGovernedApp()));
        UpdateApplicationRequest req = new UpdateApplicationRequest();
        req.setLmsProductCode("EXISTING_CODE");

        var response = service.updateApplication(appId, req);
        assertThat(response).isNotNull();
    }

    @Test
    void resendingSamePinnedWorkflowId_onCategoryGovernedApp_isNoOp() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(categoryGovernedApp()));
        UpdateApplicationRequest req = new UpdateApplicationRequest();
        req.setWorkflowId(workflowId); // same workflow the wizard already knows about

        var response = service.updateApplication(appId, req);
        assertThat(response).isNotNull();
    }

    @Test
    void resendingSameCreditVintage_onCategoryGovernedApp_isNoOp() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(categoryGovernedApp()));
        UpdateApplicationRequest req = new UpdateApplicationRequest();
        req.setCreditVintage("EXISTING_CUSTOMER");

        var response = service.updateApplication(appId, req);
        assertThat(response).isNotNull();
    }
}
