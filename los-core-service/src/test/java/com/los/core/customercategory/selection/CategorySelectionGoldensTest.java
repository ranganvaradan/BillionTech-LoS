package com.los.core.customercategory.selection;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.customercategory.ConfigLifecycleStatus;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Category eligibility / disambiguation / selection goldens.
 * Does not invoke W4/W5/W6, Policy, or Scorecard.
 */
@ExtendWith(MockitoExtension.class)
class CategorySelectionGoldensTest {

    @Mock CustomerCategoryRepository categoryRepository;
    @Mock LoanApplicationRepository applicationRepository;
    @Mock WorkflowConfigRepository workflowConfigRepository;
    @Mock CiPolicyApplicabilityRepository applicabilityRepository;
    @Mock CiPolicyDocumentRepository policyDocumentRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;
    @Mock ApplicationCategoryDisambiguationAnswerRepository answerRepository;

    CustomerCategoryEligibilityService eligibilityService;
    CategoryDisambiguationService disambiguationService;
    CategorySelectionService selectionService;

    Map<UUID, CustomerCategoryEntity> cats = new ConcurrentHashMap<>();
    Map<UUID, LoanApplication> apps = new ConcurrentHashMap<>();
    Map<String, ApplicationCategoryDisambiguationAnswerEntity> answers = new ConcurrentHashMap<>();
    Map<UUID, WorkflowConfig> workflows = new ConcurrentHashMap<>();

    UUID policyA = UUID.randomUUID();
    UUID policyB = UUID.randomUUID();
    UUID wfStarter = UUID.randomUUID();
    UUID wfBank = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eligibilityService = new CustomerCategoryEligibilityService(categoryRepository, applicationRepository);
        disambiguationService = new CategoryDisambiguationService(answerRepository);
        CategoryConfigurationPinValidator pinValidator = new CategoryConfigurationPinValidator(
                workflowConfigRepository, applicabilityRepository, policyDocumentRepository, scorecardRepository);
        selectionService = new CategorySelectionService(
                applicationRepository, categoryRepository,
                eligibilityService, disambiguationService, pinValidator);

        lenient().when(categoryRepository.findByStatus(any())).thenAnswer(inv ->
                cats.values().stream()
                        .filter(c -> c.getStatus() == inv.getArgument(0))
                        .collect(Collectors.toList()));
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
        lenient().when(answerRepository.findByApplicationIdOrderByCreatedAtAsc(any())).thenAnswer(inv ->
                answers.values().stream()
                        .filter(a -> inv.getArgument(0).equals(a.getApplicationId()))
                        .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                        .collect(Collectors.toList()));
        lenient().when(answerRepository.findByApplicationIdAndQuestionId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(answers.get(inv.getArgument(0) + "|" + inv.getArgument(1))));
        lenient().when(answerRepository.save(any())).thenAnswer(inv -> {
            ApplicationCategoryDisambiguationAnswerEntity a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            if (a.getCreatedAt() == null) {
                a.setCreatedAt(Instant.now());
            }
            answers.put(a.getApplicationId() + "|" + a.getQuestionId(), a);
            return a;
        });

        WorkflowConfig w1 = new WorkflowConfig();
        w1.setId(wfStarter);
        w1.setVersion(1);
        w1.setName("Starter WF");
        w1.setActive(true);
        workflows.put(wfStarter, w1);
        WorkflowConfig w2 = new WorkflowConfig();
        w2.setId(wfBank);
        w2.setVersion(2);
        w2.setName("Bank WF");
        w2.setActive(true);
        workflows.put(wfBank, w2);

        lenient().when(applicabilityRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            CiPolicyApplicability a = new CiPolicyApplicability();
            a.setId(id);
            a.setPolicyDocumentId(id);
            a.setBusinessStatus("ACTIVE");
            a.setPolicyName("policy");
            a.setPolicyVersionLabel("1.0");
            return Optional.of(a);
        });
        lenient().when(policyDocumentRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            CiPolicyDocument d = new CiPolicyDocument();
            d.setId(id);
            d.setName("doc");
            d.setScorecardId(null);
            return Optional.of(d);
        });
        lenient().when(scorecardRepository.findById(any())).thenReturn(Optional.empty());
    }

    private CustomerCategoryEntity cat(String code, String name, UUID policy, UUID wf,
                                       List<String> financialRoutes, boolean draft) {
        Map<String, Object> gov = new LinkedHashMap<>();
        gov.put("proposition", Map.of(
                "customerFacingName", name,
                "shortDescription", name + " journey",
                "requirementsSummary", "Provide data via configured route",
                "displayOrder", code.contains("BANK") ? 20 : 10,
                "allowAutoSingleMatch", true,
                "benefits", List.of("Clear customer journey")));
        gov.put("disambiguation", Map.of(
                "attributes", Map.of(
                        SafeDisambiguationCatalogue.ATTR_FINANCIAL_DATA_ROUTE, financialRoutes)));
        CustomerCategoryEntity c = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID())
                .code(code)
                .versionNo(1)
                .name(name)
                .description(name)
                .status(draft ? ConfigLifecycleStatus.DRAFT : ConfigLifecycleStatus.ACTIVE)
                .borrowerType("INDIVIDUAL")
                .loanProduct("BUSINESS_TERM_LOAN")
                .intakeSegment("BORROWER")
                .minAmount(new BigDecimal("20000"))
                .maxAmount(new BigDecimal("500000"))
                .policyApplicabilityId(policy)
                .policyDocumentId(policy)
                .policyVersionLabel("1.0")
                .workflowId(wf)
                .workflowVersion(wf.equals(wfStarter) ? 1 : 2)
                .governanceJson(gov)
                .inferenceNotes(new LinkedHashMap<>())
                .build();
        cats.put(c.getId(), c);
        return c;
    }

    private LoanApplication app(BigDecimal amount) {
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

    @Test
    void identicalDimensionsBothEligible_starterAndBank() {
        cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        cat("BANK_STARTER", "Bank Starter", policyB, wfBank,
                List.of(SafeDisambiguationCatalogue.OPT_BANK_AA), true);
        LoanApplication a = app(new BigDecimal("300000"));

        var result = selectionService.evaluate(a.getId(), true);
        assertEquals(2, result.eligible().size());
        assertTrue(result.eligible().stream().anyMatch(e -> "STARTER_LOAN".equals(e.code())));
        assertTrue(result.eligible().stream().anyMatch(e -> "BANK_STARTER".equals(e.code())));
        assertEquals(CategorySelectionState.DISAMBIGUATION_REQUIRED, result.state());
    }

    @Test
    void starterBankStarter_bankAnswer_selectsBank() {
        CustomerCategoryEntity starter = cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        CustomerCategoryEntity bank = cat("BANK_STARTER", "Bank Starter", policyB, wfBank,
                List.of(SafeDisambiguationCatalogue.OPT_BANK_AA), true);
        LoanApplication a = app(new BigDecimal("300000"));

        var afterQ = selectionService.answer(a.getId(),
                new CategorySelectionDtos.AnswerRequest(
                        SafeDisambiguationCatalogue.Q_FINANCIAL_DATA_ROUTE,
                        SafeDisambiguationCatalogue.OPT_BANK_AA,
                        "cust-1", "CUSTOMER"),
                true);
        assertEquals(1, afterQ.eligible().size());
        assertEquals("BANK_STARTER", afterQ.eligible().get(0).code());
        assertEquals(CategorySelectionState.AUTO_SINGLE_MATCH, afterQ.state());

        var selected = selectionService.autoSelectIfSingle(a.getId(), "cust-1", true);
        assertEquals(CategorySelectionState.CATEGORY_SELECTED, selected.state());
        assertNotNull(selected.selected());
        assertEquals(bank.getId(), selected.selected().categoryId());
        assertEquals(policyB, selected.selected().policyApplicabilityId());
        assertEquals(wfBank, selected.selected().workflowId());
        assertEquals(CategorySelectionSource.AUTO_SINGLE_ELIGIBLE, selected.selected().selectionSource());
        assertFalse((Boolean) selected.diagnostics().get("w4Triggered"));
        assertFalse((Boolean) selected.diagnostics().get("w6Triggered"));
        assertFalse((Boolean) selected.diagnostics().get("policyExecuted"));

        LoanApplication pinned = apps.get(a.getId());
        assertEquals(bank.getId(), pinned.getSelectedCustomerCategoryId());
        assertEquals(Integer.valueOf(1), pinned.getSelectedCustomerCategoryVersion());
        assertEquals("CATEGORY_SELECTION", pinned.getWorkflowResolutionSource());
        assertEquals(wfBank, pinned.getWorkflowId());
    }

    @Test
    void bothValid_explicitPropositionRequired() {
        cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        cat("BANK_STARTER", "Bank Starter", policyB, wfBank,
                List.of(SafeDisambiguationCatalogue.OPT_BANK_AA), true);
        LoanApplication a = app(new BigDecimal("300000"));

        var after = selectionService.answer(a.getId(),
                new CategorySelectionDtos.AnswerRequest(
                        SafeDisambiguationCatalogue.Q_FINANCIAL_DATA_ROUTE,
                        SafeDisambiguationCatalogue.OPT_BOTH,
                        "cust-1", "CUSTOMER"),
                true);
        assertEquals(2, after.eligible().size());
        assertEquals(CategorySelectionState.EXPLICIT_PROPOSITION_SELECTION_REQUIRED, after.state());
        assertNull(after.selected());
        assertFalse(after.propositions().isEmpty());
    }

    @Test
    void oneMatch_autoSingle() {
        cat("ONLY_ONE", "Only One", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        LoanApplication a = app(new BigDecimal("300000"));
        var eval = selectionService.evaluate(a.getId(), true);
        assertEquals(CategorySelectionState.AUTO_SINGLE_MATCH, eval.state());
        var selected = selectionService.autoSelectIfSingle(a.getId(), "sys", true);
        assertEquals(CategorySelectionState.CATEGORY_SELECTED, selected.state());
        assertEquals(CategorySelectionSource.AUTO_SINGLE_ELIGIBLE, selected.selected().selectionSource());
    }

    @Test
    void noSafeQuestion_explicitRequired() {
        // Two cats, no disambiguation attributes
        CustomerCategoryEntity c1 = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("A").versionNo(1).name("A")
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL").loanProduct("BUSINESS_TERM_LOAN").intakeSegment("BORROWER")
                .minAmount(new BigDecimal("1")).maxAmount(new BigDecimal("999999"))
                .policyApplicabilityId(policyA).policyDocumentId(policyA).policyVersionLabel("1")
                .workflowId(wfStarter).workflowVersion(1)
                .governanceJson(new LinkedHashMap<>(Map.of("proposition", Map.of("allowAutoSingleMatch", true))))
                .inferenceNotes(new LinkedHashMap<>())
                .build();
        CustomerCategoryEntity c2 = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID()).code("B").versionNo(1).name("B")
                .status(ConfigLifecycleStatus.DRAFT)
                .borrowerType("INDIVIDUAL").loanProduct("BUSINESS_TERM_LOAN").intakeSegment("BORROWER")
                .minAmount(new BigDecimal("1")).maxAmount(new BigDecimal("999999"))
                .policyApplicabilityId(policyB).policyDocumentId(policyB).policyVersionLabel("1")
                .workflowId(wfBank).workflowVersion(2)
                .governanceJson(new LinkedHashMap<>(Map.of("proposition", Map.of("allowAutoSingleMatch", true))))
                .inferenceNotes(new LinkedHashMap<>())
                .build();
        cats.put(c1.getId(), c1);
        cats.put(c2.getId(), c2);
        LoanApplication a = app(new BigDecimal("300000"));
        var eval = selectionService.evaluate(a.getId(), true);
        assertEquals(CategorySelectionState.EXPLICIT_PROPOSITION_SELECTION_REQUIRED, eval.state());
        assertNull(eval.nextQuestion());
    }

    @Test
    void noMatch_structuredReasons() {
        cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        LoanApplication a = app(new BigDecimal("300000"));
        a.setLoanProduct("PERSONAL_LOAN");
        var eval = selectionService.evaluate(a.getId(), true);
        assertEquals(CategorySelectionState.NO_ELIGIBLE_CATEGORY, eval.state());
        assertTrue(eval.eligible().isEmpty());
        assertFalse(eval.noMatchReasons().isEmpty());
    }

    @Test
    void ineligibleExplicitSelection_rejected() {
        CustomerCategoryEntity starter = cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        cat("BANK_STARTER", "Bank Starter", policyB, wfBank,
                List.of(SafeDisambiguationCatalogue.OPT_BANK_AA), true);
        LoanApplication a = app(new BigDecimal("300000"));
        // Answer bank → starter not eligible
        selectionService.answer(a.getId(),
                new CategorySelectionDtos.AnswerRequest(
                        SafeDisambiguationCatalogue.Q_FINANCIAL_DATA_ROUTE,
                        SafeDisambiguationCatalogue.OPT_BANK_AA, "c", "CUSTOMER"),
                true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                selectionService.select(a.getId(),
                        new CategorySelectionDtos.SelectRequest(
                                starter.getId(), "c", "CUSTOMER", null,
                                CategorySelectionSource.CUSTOMER_SELECTED),
                        true));
        assertEquals("CATEGORY_NOT_ELIGIBLE", ex.getReason());
        assertNull(apps.get(a.getId()).getSelectedCustomerCategoryId());
    }

    @Test
    void amountInclusiveBoundaries() {
        cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        assertTrue(CustomerCategoryEligibilityService.amountMatch(
                new BigDecimal("20000"), new BigDecimal("500000"), new BigDecimal("20000")));
        assertTrue(CustomerCategoryEligibilityService.amountMatch(
                new BigDecimal("20000"), new BigDecimal("500000"), new BigDecimal("500000")));
        assertFalse(CustomerCategoryEligibilityService.amountMatch(
                new BigDecimal("20000"), new BigDecimal("500000"), new BigDecimal("19999")));
    }

    @Test
    void workflowConflict_failClosed() {
        CustomerCategoryEntity bank = cat("BANK_STARTER", "Bank Starter", policyB, wfBank,
                List.of(SafeDisambiguationCatalogue.OPT_BANK_AA), true);
        LoanApplication a = app(new BigDecimal("300000"));
        a.setWorkflowId(wfStarter); // different from bank
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                selectionService.select(a.getId(),
                        new CategorySelectionDtos.SelectRequest(
                                bank.getId(), "rm", "RM", "ok",
                                CategorySelectionSource.RM_SELECTED),
                        true));
        assertEquals("CATEGORY_WORKFLOW_CONFLICT", ex.getReason());
    }

    @Test
    void saveResume_restoresAnswers() {
        cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        cat("BANK_STARTER", "Bank Starter", policyB, wfBank,
                List.of(SafeDisambiguationCatalogue.OPT_BANK_AA), true);
        LoanApplication a = app(new BigDecimal("300000"));
        selectionService.answer(a.getId(),
                new CategorySelectionDtos.AnswerRequest(
                        SafeDisambiguationCatalogue.Q_FINANCIAL_DATA_ROUTE,
                        SafeDisambiguationCatalogue.OPT_BANK_AA, "c", "CUSTOMER"),
                true);
        // Re-evaluate as resume
        var resumed = selectionService.evaluate(a.getId(), true);
        assertEquals(1, resumed.eligible().size());
        assertEquals("BANK_STARTER", resumed.eligible().get(0).code());
        assertTrue(resumed.diagnostics().get("priorAnswers") instanceof Map);
    }

    @Test
    void rmSelection_audited() {
        CustomerCategoryEntity only = cat("ONLY", "Only", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        LoanApplication a = app(new BigDecimal("300000"));
        var selected = selectionService.select(a.getId(),
                new CategorySelectionDtos.SelectRequest(
                        only.getId(), "rm-42", "RM", "Customer prefers this",
                        CategorySelectionSource.RM_SELECTED),
                true);
        assertEquals(CategorySelectionSource.RM_SELECTED, selected.selected().selectionSource());
        assertEquals("rm-42", apps.get(a.getId()).getCategorySelectedBy());
        assertEquals("Customer prefers this", apps.get(a.getId()).getCategorySelectionReason());
    }

    @Test
    void draftCategoriesNotEligibleWithoutSimulationFlag() {
        cat("STARTER_LOAN", "Starter Loan", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        LoanApplication a = app(new BigDecimal("300000"));
        var eval = selectionService.evaluate(a.getId(), false);
        assertEquals(CategorySelectionState.NO_ELIGIBLE_CATEGORY, eval.state());
    }

    @Test
    void handoff_doesNotTriggerW4() {
        CustomerCategoryEntity only = cat("ONLY", "Only", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        LoanApplication a = app(new BigDecimal("300000"));
        selectionService.autoSelectIfSingle(a.getId(), "sys", true);
        var handoff = selectionService.handoff(a.getId());
        assertEquals(only.getId(), handoff.categoryId());
        assertEquals(policyA, handoff.policyApplicabilityId());
        assertEquals(wfStarter, handoff.workflowId());
    }

    @Test
    void missingPolicyDocumentId_notEligible() {
        CustomerCategoryEntity c = cat("NO_DOC", "No Doc", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        c.setPolicyDocumentId(null);
        LoanApplication a = app(new BigDecimal("300000"));
        var eval = selectionService.evaluate(a.getId(), true);
        assertEquals(CategorySelectionState.NO_ELIGIBLE_CATEGORY, eval.state());
    }

    @Test
    void pinValidationFailure_writesNothing() {
        CustomerCategoryEntity only = cat("ONLY", "Only", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        LoanApplication a = app(new BigDecimal("300000"));
        when(policyDocumentRepository.findById(policyA)).thenReturn(Optional.empty());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                selectionService.select(a.getId(),
                        new CategorySelectionDtos.SelectRequest(
                                only.getId(), "c", "CUSTOMER", null,
                                CategorySelectionSource.CUSTOMER_SELECTED),
                        true));
        assertEquals("POLICY_DOCUMENT_NOT_FOUND", ex.getReason());
        LoanApplication after = apps.get(a.getId());
        assertNull(after.getSelectedCustomerCategoryId());
        assertNull(after.getWorkflowId());
        assertNull(after.getSelectedPolicyApplicabilityId());
        assertNull(after.getSelectedPolicyDocumentId());
    }

    @Test
    void missingAmount_pendingWithoutWorkflow() {
        cat("ONLY", "Only", policyA, wfStarter,
                List.of(SafeDisambiguationCatalogue.OPT_FINANCIAL_STATEMENTS), true);
        LoanApplication a = app(null);
        var eval = selectionService.evaluate(a.getId(), true);
        assertEquals(CategorySelectionState.NO_ELIGIBLE_CATEGORY, eval.state());
        assertTrue(eval.noMatchReasons().contains("AMOUNT"));
        assertNull(apps.get(a.getId()).getWorkflowId());
    }
}
