package com.los.core.customercategory.selection;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.customercategory.ConfigLifecycleStatus;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.model.dto.response.ApplicationResponse;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.loan.LoanApplicationServiceImpl;
import com.los.core.service.workflow.WorkflowResolutionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * APPLICATION-CANONICAL-WORKFLOW-VISIBILITY-1 — display authority uses pinned application workflow only.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationCanonicalWorkflowDisplayTest {

    @Mock CustomerCategoryRepository categoryRepository;
    @Mock LoanApplicationRepository applicationRepository;
    @Mock WorkflowConfigRepository workflowConfigRepository;
    @Mock CiPolicyApplicabilityRepository applicabilityRepository;
    @Mock CiPolicyDocumentRepository policyDocumentRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;
    @Mock ApplicationCategoryDisambiguationAnswerRepository answerRepository;

    CategorySelectionService selectionService;

    Map<UUID, CustomerCategoryEntity> cats = new ConcurrentHashMap<>();
    Map<UUID, LoanApplication> apps = new ConcurrentHashMap<>();
    Map<UUID, WorkflowConfig> workflows = new ConcurrentHashMap<>();

    UUID policyId = UUID.randomUUID();
    UUID pinnedWfId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CustomerCategoryEligibilityService eligibilityService =
                new CustomerCategoryEligibilityService(categoryRepository, applicationRepository);
        CategoryDisambiguationService disambiguationService = new CategoryDisambiguationService(answerRepository);
        CategoryConfigurationPinValidator pinValidator = new CategoryConfigurationPinValidator(
                workflowConfigRepository, applicabilityRepository, policyDocumentRepository, scorecardRepository);
        selectionService = new CategorySelectionService(
                applicationRepository, categoryRepository, workflowConfigRepository,
                eligibilityService, disambiguationService, pinValidator);

        lenient().when(categoryRepository.findByStatus(any())).thenAnswer(inv ->
                cats.values().stream().filter(c -> c.getStatus() == inv.getArgument(0)).toList());
        lenient().when(categoryRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(cats.get(inv.getArgument(0))));
        lenient().when(applicationRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(apps.get(inv.getArgument(0))));
        lenient().when(applicationRepository.save(any())).thenAnswer(inv -> {
            LoanApplication a = inv.getArgument(0);
            apps.put(a.getId(), a);
            return a;
        });
        lenient().when(workflowConfigRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(workflows.get(inv.getArgument(0))));
        lenient().when(answerRepository.findByApplicationIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        lenient().when(answerRepository.findByApplicationIdAndQuestionId(any(), any())).thenReturn(Optional.empty());

        WorkflowConfig pinned = new WorkflowConfig();
        pinned.setId(pinnedWfId);
        pinned.setVersion(3);
        pinned.setName("Pinned Display WF");
        pinned.setActive(true);
        pinned.setBorrowerType(BorrowerType.INDIVIDUAL.name());
        pinned.setLoanProduct("BUSINESS_TERM_LOAN");
        workflows.put(pinnedWfId, pinned);

        lenient().when(applicabilityRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            CiPolicyApplicability a = new CiPolicyApplicability();
            a.setId(id);
            a.setPolicyDocumentId(id);
            a.setBusinessStatus("ACTIVE");
            return Optional.of(a);
        });
        lenient().when(policyDocumentRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            CiPolicyDocument d = new CiPolicyDocument();
            d.setId(id);
            d.setScorecardId(null);
            return Optional.of(d);
        });
        lenient().when(scorecardRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void categorySelected_workflowPinnedOnApplication() {
        CustomerCategoryEntity cat = seedCategory("DISPLAY_CAT", "Display Category", pinnedWfId);
        LoanApplication app = seedApp(new BigDecimal("500000"));
        var result = selectionService.select(
                app.getId(),
                new CategorySelectionDtos.SelectRequest(
                        cat.getId(), "user", "CUSTOMER", null, CategorySelectionSource.CUSTOMER_SELECTED),
                true);
        LoanApplication saved = apps.get(app.getId());

        assertNotNull(result.selected());
        assertEquals(CategorySelectionState.CATEGORY_SELECTED, result.state());
        assertEquals(pinnedWfId, saved.getWorkflowId());
        assertEquals(3, saved.getWorkflowVersion());
        assertEquals(WorkflowResolutionSource.CATEGORY_SELECTION.name(), saved.getWorkflowResolutionSource());
    }

    @Test
    void handoffDisplayMatchesPinnedWorkflowIdAndVersion() {
        CustomerCategoryEntity cat = seedCategory("HANDOFF_CAT", "Handoff Category", pinnedWfId);
        LoanApplication app = seedApp(new BigDecimal("500000"));
        selectionService.select(
                app.getId(),
                new CategorySelectionDtos.SelectRequest(
                        cat.getId(), "user", "CUSTOMER", null, CategorySelectionSource.CUSTOMER_SELECTED),
                true);
        var handoff = selectionService.handoff(app.getId());

        assertEquals(pinnedWfId, handoff.workflowId());
        assertEquals(3, handoff.workflowVersion());
        assertEquals("Pinned Display WF", handoff.workflowName());
        assertEquals("Handoff Category", handoff.categoryDisplayName());
    }

    @Test
    void applicationResponseDisplayUsesExactPinnedWorkflowLookup() throws Exception {
        CustomerCategoryEntity cat = seedCategory("API_CAT", "API Category", pinnedWfId);
        LoanApplication app = seedApp(new BigDecimal("500000"));
        selectionService.select(
                app.getId(),
                new CategorySelectionDtos.SelectRequest(
                        cat.getId(), "user", "CUSTOMER", null, CategorySelectionSource.CUSTOMER_SELECTED),
                true);
        LoanApplication saved = apps.get(app.getId());

        ApplicationResponse response = invokeToResponse(saved);

        assertEquals(pinnedWfId, response.getWorkflowId());
        assertEquals(3, response.getWorkflowVersion());
        assertEquals("Pinned Display WF", response.getWorkflowName());
        assertEquals("API_CAT", response.getSelectedCustomerCategoryCode());
        assertEquals(cat.getVersionNo(), response.getSelectedCustomerCategoryVersion());
        assertEquals("API Category", response.getSelectedCustomerCategoryDisplayName());
        verify(workflowConfigRepository, atLeastOnce()).findById(pinnedWfId);
    }

    @Test
    void historicalApplicationPin_notRewrittenOnRead() throws Exception {
        LoanApplication historical = seedApp(new BigDecimal("200000"));
        historical.setWorkflowId(pinnedWfId);
        historical.setWorkflowVersion(3);
        historical.setWorkflowResolutionSource("LEGACY_PRODUCT_CONFIG");
        historical.setSelectedCustomerCategoryId(null);
        apps.put(historical.getId(), historical);

        ApplicationResponse response = invokeToResponse(historical);

        assertEquals(pinnedWfId, response.getWorkflowId());
        assertEquals(3, response.getWorkflowVersion());
        assertEquals("Pinned Display WF", response.getWorkflowName());
        assertNull(response.getSelectedCustomerCategoryId());
        assertNull(response.getSelectedCustomerCategoryCode());
    }

    private ApplicationResponse invokeToResponse(LoanApplication app) throws Exception {
        LoanApplicationServiceImpl applicationService = new LoanApplicationServiceImpl(
                applicationRepository,
                mock(com.los.core.service.audit.AuditService.class),
                mock(com.los.core.service.audit.RecordAuditService.class),
                mock(com.los.core.service.credit.CreditControlService.class),
                mock(com.los.core.service.underwriting.UnderwritingEvaluationService.class),
                mock(com.los.core.repository.CreditAppraisalMemoRepository.class),
                mock(com.los.core.service.loan.intake.ApplicationCustomerIdResolver.class),
                mock(com.los.core.service.loan.intake.IntakeMetadataEnricher.class),
                mock(com.los.core.service.loan.intake.ApplicationSubmitIdentityValidator.class),
                mock(com.los.core.service.loan.ApplicationInputChangeTracker.class),
                workflowConfigRepository,
                mock(com.los.core.service.workflow.ApplicationWorkflowResolver.class),
                categoryRepository,
                mock(com.los.lms.repository.ExternalProductMappingRepository.class));
        Method m = LoanApplicationServiceImpl.class.getDeclaredMethod("toResponse", LoanApplication.class);
        m.setAccessible(true);
        return (ApplicationResponse) m.invoke(applicationService, app);
    }

    private CustomerCategoryEntity seedCategory(String code, String name, UUID workflowId) {
        Map<String, Object> gov = new LinkedHashMap<>();
        gov.put("proposition", Map.of(
                "customerFacingName", name,
                "shortDescription", name + " journey",
                "requirementsSummary", "Provide data",
                "displayOrder", 10,
                "allowAutoSingleMatch", true,
                "benefits", List.of("Clear customer journey")));
        gov.put("disambiguation", Map.of("attributes", Map.of()));
        CustomerCategoryEntity c = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID())
                .code(code)
                .versionNo(1)
                .name(name)
                .description(name)
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL")
                .loanProduct("BUSINESS_TERM_LOAN")
                .intakeSegment("BORROWER")
                .minAmount(new BigDecimal("20000"))
                .maxAmount(new BigDecimal("5000000"))
                .policyApplicabilityId(policyId)
                .policyDocumentId(policyId)
                .policyVersionLabel("1.0")
                .workflowId(workflowId)
                .workflowVersion(3)
                .governanceJson(gov)
                .inferenceNotes(new LinkedHashMap<>())
                .build();
        cats.put(c.getId(), c);
        return c;
    }

    private LoanApplication seedApp(BigDecimal amount) {
        LoanApplication a = new LoanApplication();
        a.setId(UUID.randomUUID());
        a.setCustomerId(UUID.randomUUID());
        a.setBorrowerType(BorrowerType.INDIVIDUAL);
        a.setLoanProduct("BUSINESS_TERM_LOAN");
        a.setIntakeSegment(IntakeSegment.BORROWER);
        a.setRequestedAmount(amount);
        apps.put(a.getId(), a);
        return a;
    }
}
