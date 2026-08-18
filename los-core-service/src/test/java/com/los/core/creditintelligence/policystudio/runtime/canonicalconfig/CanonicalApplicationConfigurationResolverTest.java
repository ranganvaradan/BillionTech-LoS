package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import com.los.core.creditintelligence.bureau.domain.CiBureauReport;
import com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraph;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraphOperand;
import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyApplicability;
import com.los.core.creditintelligence.policystudio.lifecycle.repository.CiPolicyApplicabilityRepository;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinitionRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphOperandRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphRepository;
import com.los.core.customercategory.ConfigLifecycleStatus;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.customercategory.CustomerCategoryRepository;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.repository.WorkflowConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CanonicalApplicationConfigurationResolverTest {

    static final UUID VIKASAM_CATEGORY_ID = UUID.fromString("b8396924-0315-4645-9c0b-b1a487a32502");
    static final UUID VIKASAM_WORKFLOW_V1 = UUID.fromString("3b1e0488-c33a-48d5-b494-5b77eec30b8c");
    static final UUID VIKASAM_APPLICABILITY = UUID.fromString("5908a11e-bf5e-4870-a1f3-2f5547f6568f");
    static final UUID VIKASAM_DOCUMENT = UUID.fromString("4543e643-c3a0-4a57-a92c-370dff8b2fa9");
    static final UUID VIKASAM_SCORECARD = UUID.fromString("cc38f5a0-fab7-4001-8167-5a8f47d2487a");
    static final UUID PRODUCT_PRIORITY_SCORECARD = UUID.fromString("d3320000-0000-4000-a000-000000000002");

    @Mock CustomerCategoryRepository customerCategoryRepository;
    @Mock WorkflowConfigRepository workflowConfigRepository;
    @Mock CiPolicyApplicabilityRepository applicabilityRepository;
    @Mock CiPolicyDocumentRepository policyDocumentRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;
    @Mock CiPolicyRuleGraphRepository ruleGraphRepository;
    @Mock CiPolicyRuleGraphOperandRepository operandRepository;
    @Mock CiGacatDerivedCalculationDefinitionRepository calculationDefinitionRepository;
    @Mock CiBureauReportRepository bureauReportRepository;

    CanonicalApplicationConfigurationResolver resolver;

    UUID categoryId;
    UUID workflowId;
    UUID applicabilityId;
    UUID documentId;
    UUID scorecardId;
    UUID authoredDefId;
    UUID bureauReportId;

    @BeforeEach
    void setUp() {
        resolver = new CanonicalApplicationConfigurationResolver(
                customerCategoryRepository,
                workflowConfigRepository,
                applicabilityRepository,
                policyDocumentRepository,
                scorecardRepository,
                ruleGraphRepository,
                operandRepository,
                calculationDefinitionRepository,
                bureauReportRepository);
        resolver.parameterLookup = id -> Optional.of(new CanonicalParameterDefinition(
                id, id, "Bureau",
                id.contains("authored") ? CanonicalParameterDefinition.DERIVED : CanonicalParameterDefinition.RAW,
                null, null, null, null, List.of(), null, List.of(), null, null, null));
        categoryId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        applicabilityId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        scorecardId = UUID.randomUUID();
        authoredDefId = UUID.randomUUID();
        bureauReportId = UUID.randomUUID();
        lenient().when(ruleGraphRepository.findByPolicyDocumentIdAndDocumentVersion(any(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(calculationDefinitionRepository.findByCanonicalParameterId(any()))
                .thenReturn(List.of());
        lenient().when(bureauReportRepository.findByApplicationId(any())).thenReturn(List.of());
        lenient().when(operandRepository.findByGraphId(any())).thenReturn(List.of());
    }

    @Test
    void exactCategoryPinResolves() {
        Fixture fx = fullyPinned();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isTrue();
        assertThat(r.configuration().customerCategoryId()).isEqualTo(categoryId);
        assertThat(r.configuration().customerCategoryVersion()).isEqualTo(1);
        verify(customerCategoryRepository, never()).findFirstByCodeOrderByVersionNoDesc(any());
    }

    @Test
    void missingCategoryDoesNotChooseLatest() {
        LoanApplication app = baseApp();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.CUSTOMER_CATEGORY_NOT_PINNED.name());
        verify(customerCategoryRepository, never()).findFirstByCodeOrderByVersionNoDesc(any());
        verify(customerCategoryRepository, never()).findByStatus(any());
        assertThat(app.getSelectedCustomerCategoryId()).isNull();
    }

    @Test
    void exactWorkflowUuidResolves() {
        Fixture fx = fullyPinned();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.outcomes()).containsEntry("workflow", CanonicalApplicationConfigurationResolver.WORKFLOW_VERSION_RESOLVED);
        assertThat(r.configuration().workflowVersionId()).isEqualTo(workflowId);
        verify(workflowConfigRepository, never())
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(any(), any(), any());
    }

    @Test
    void missingWorkflowDoesNotChooseLatestActive() {
        Fixture fx = fullyPinned();
        fx.app.setWorkflowId(null);
        fx.app.setWorkflowVersion(null);
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.WORKFLOW_VERSION_NOT_PINNED.name());
        assertThat(r.outcomes()).containsEntry("workflow", CanonicalApplicationConfigurationResolver.WORKFLOW_VERSION_NOT_PINNED);
        verify(workflowConfigRepository, never())
                .findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(any(), any(), any());
    }

    @Test
    void exactApplicabilityResolves() {
        Fixture fx = fullyPinned();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.configuration().policyApplicabilityId()).isEqualTo(applicabilityId);
        assertThat(r.outcomes()).containsEntry("policyApplicability", "POLICY_APPLICABILITY_RESOLVED");
    }

    @Test
    void brokenApplicabilityFailsExplicitly() {
        Fixture fx = fullyPinned();
        fx.app.setSelectedPolicyApplicabilityId(UUID.randomUUID());
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.POLICY_APPLICABILITY_NOT_FOUND.name());
        verify(applicabilityRepository, never()).findResolvableByTenant(any());
    }

    @Test
    void exactPolicyDocumentResolves() {
        Fixture fx = fullyPinned();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.configuration().policyDocumentId()).isEqualTo(documentId);
        assertThat(r.configuration().policyDocumentVersion()).isEqualTo(1);
    }

    @Test
    void policyLinkedScorecardResolves() {
        Fixture fx = fullyPinned();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.configuration().scorecardId()).isEqualTo(scorecardId);
        assertThat(r.configuration().scorecardExplicitlyAbsent()).isFalse();
        verify(scorecardRepository, never())
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(any(), any());
    }

    @Test
    void productPriorityScorecardCannotOverridePolicyLinkedScorecard() {
        Fixture fx = fullyPinned();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.configuration().scorecardId()).isEqualTo(scorecardId);
        assertThat(r.configuration().scorecardId()).isNotEqualTo(PRODUCT_PRIORITY_SCORECARD);
        verify(scorecardRepository, never())
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(any(), any());
    }

    @Test
    void noScorecardPolicyDistinguishedFromBrokenScorecardFk() {
        Fixture none = fullyPinned();
        none.document.setScorecardId(null);
        CanonicalApplicationConfigurationResolution absent = resolver.resolve(none.app);
        assertThat(absent.resolved()).isTrue();
        assertThat(absent.configuration().scorecardExplicitlyAbsent()).isTrue();
        assertThat(absent.configuration().scorecardRequired()).isFalse();
        assertThat(absent.configuration().scorecardId()).isNull();

        Fixture broken = fullyPinned();
        UUID missing = UUID.randomUUID();
        broken.document.setScorecardId(missing);
        when(scorecardRepository.findById(missing)).thenReturn(Optional.empty());
        CanonicalApplicationConfigurationResolution fail = resolver.resolve(broken.app);
        assertThat(fail.resolved()).isFalse();
        assertThat(fail.reasonCodes()).contains(CanonicalResolutionFailureCode.SCORECARD_VERSION_NOT_RESOLVABLE.name());
        assertThat(fail.configuration().scorecardExplicitlyAbsent()).isFalse();
    }

    @Test
    void authoredCalculationExactVersionIsPinned() {
        Fixture fx = fullyPinned();
        UUID graphId = UUID.randomUUID();
        CiPolicyRuleGraph graph = CiPolicyRuleGraph.builder()
                .id(graphId).policyDocumentId(documentId).documentVersion(1).graphHash("h").build();
        when(ruleGraphRepository.findByPolicyDocumentIdAndDocumentVersion(documentId, 1))
                .thenReturn(Optional.of(graph));
        when(operandRepository.findByGraphId(graphId)).thenReturn(List.of(
                CiPolicyRuleGraphOperand.builder()
                        .id(UUID.randomUUID()).graphId(graphId).nodeId(UUID.randomUUID())
                        .operandPath("r.x").originalToken("authored.dti")
                        .canonicalParameterId("authored.dti").resolutionStatus("RESOLVED").build()));
        CiGacatDerivedCalculationDefinition def = CiGacatDerivedCalculationDefinition.builder()
                .id(authoredDefId)
                .canonicalParameterId("authored.dti")
                .calculationType(CanonicalCalculationPin.AUTHORED_EXPRESSION)
                .status("TESTED")
                .versionNo(3)
                .build();
        when(calculationDefinitionRepository.findByCanonicalParameterId("authored.dti"))
                .thenReturn(List.of(def));
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isTrue();
        assertThat(r.configuration().calculationDefinitionPins())
                .anyMatch(p -> "authored.dti".equals(p.parameterId())
                        && authoredDefId.equals(p.calculationDefinitionId())
                        && Integer.valueOf(3).equals(p.calculationDefinitionVersion())
                        && !p.fallbackRequired());
    }

    @Test
    void latestForCannotChangeFrozenPackage() {
        Fixture fx = fullyPinned();
        Map<UUID, CanonicalApplicationConfigurationEntity> store = new ConcurrentHashMap<>();
        CanonicalApplicationConfigurationFreezeService freeze = freezeService(store);
        CanonicalApplicationConfigurationResolution first = freeze.freezeObservably(fx.app);
        assertThat(first.resolved()).isTrue();
        String hash = first.configuration().identityHash();
        Instant ts = first.configuration().resolutionTimestamp();
        fx.category.setName("mutated-after-freeze");
        CanonicalApplicationConfigurationResolution second = freeze.freezeObservably(fx.app);
        assertThat(second.configuration().identityHash()).isEqualTo(hash);
        assertThat(second.configuration().resolutionTimestamp()).isEqualTo(ts);
        assertThat(store.values().stream().filter(e -> "RESOLVED".equals(e.getStatus())).count()).isEqualTo(1);
    }

    @Test
    void exactBureauReportPinned() {
        Fixture fx = fullyPinned();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.configuration().bureauReportId()).isEqualTo(bureauReportId);
        verify(bureauReportRepository, never()).findFirstByApplicationIdOrderByCreatedAtDesc(any());
        verify(bureauReportRepository, never()).findByApplicationIdOrderByCreatedAtDesc(any());
    }

    @Test
    void missingReportDoesNotChooseNewest() {
        Fixture fx = fullyPinned();
        fx.workflow.setBureauEnabled(true);
        when(bureauReportRepository.findByApplicationId(fx.app.getId())).thenReturn(List.of());
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.BUREAU_REPORT_NOT_PINNED.name());
        verify(bureauReportRepository, never()).findFirstByApplicationIdOrderByCreatedAtDesc(any());
    }

    @Test
    void evaluationAsOfExplicit() {
        Fixture fx = fullyPinned();
        Instant created = Instant.parse("2026-08-10T06:30:00Z");
        fx.app.setCreatedAt(created);
        fx.app.setSubmittedAt(Instant.parse("2026-08-11T06:30:00Z"));
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.configuration().evaluationAsOf()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(r.configuration().resolutionProvenance().get("evaluationAsOf"))
                .contains("wall-clock now forbidden");
    }

    @Test
    void noLocalDateNowFallback() {
        LoanApplication app = baseApp();
        app.setCreatedAt(null);
        app.setSubmittedAt(null);
        CanonicalApplicationConfigurationResolution r = resolver.resolve(app);
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.EVALUATION_AS_OF_NOT_RESOLVABLE.name());
        if (r.configuration() != null) {
            assertThat(r.configuration().evaluationAsOf()).isNull();
        }
    }

    @Test
    void retryReturnsSameFrozenIdentityPackage() {
        Fixture fx = fullyPinned();
        Map<UUID, CanonicalApplicationConfigurationEntity> store = new ConcurrentHashMap<>();
        CanonicalApplicationConfigurationFreezeService freeze = freezeService(store);
        CanonicalApplicationConfigurationResolution a = freeze.freezeObservably(fx.app);
        CanonicalApplicationConfigurationResolution b = freeze.freezeObservably(fx.app);
        assertThat(b.configuration().identityHash()).isEqualTo(a.configuration().identityHash());
        assertThat(b.configuration().workflowVersionId()).isEqualTo(a.configuration().workflowVersionId());
        assertThat(b.configuration().policyDocumentId()).isEqualTo(a.configuration().policyDocumentId());
        assertThat(b.configuration().scorecardId()).isEqualTo(a.configuration().scorecardId());
        assertThat(b.configuration().resolutionTimestamp()).isEqualTo(a.configuration().resolutionTimestamp());
    }

    @Test
    void existingLegacyApplicationIsNotAutoMigrated() {
        LoanApplication legacy = baseApp();
        legacy.setWorkflowId(null);
        CanonicalApplicationConfigurationResolution r = resolver.resolve(legacy);
        assertThat(r.resolved()).isFalse();
        assertThat(legacy.getSelectedCustomerCategoryId()).isNull();
        assertThat(legacy.getSelectedPolicyApplicabilityId()).isNull();
        assertThat(legacy.getSelectedPolicyDocumentId()).isNull();
        assertThat(legacy.getWorkflowId()).isNull();
        verify(customerCategoryRepository, never()).findFirstByCodeOrderByVersionNoDesc(any());
        assertThat(CanonicalApplicationPinClassifier.classify(legacy, null, null, null))
                .isEqualTo(CanonicalApplicationPinClassifier.Class.LEGACY_UNPINNED);
    }

    @Test
    void resolverFailureDoesNotAlterLiveLegacyDecisionInW112() {
        LoanApplication legacy = baseApp();
        String decision = legacy.getCreditDecision();
        CanonicalApplicationConfigurationResolution r = resolver.resolve(legacy);
        assertThat(r.resolved()).isFalse();
        assertThat(legacy.getCreditDecision()).isEqualTo(decision);
        assertThat(r.toMap()).containsEntry("canonicalRuntimeUsedForLiveDecision", false);
        assertThat(r.toMap()).containsEntry("liveDecisionAuthorityUnchanged", true);
    }

    @Test
    void clientVikasamCategoryResolvesExactWorkflowPolicyWithoutChangingThem() {
        categoryId = VIKASAM_CATEGORY_ID;
        workflowId = VIKASAM_WORKFLOW_V1;
        applicabilityId = VIKASAM_APPLICABILITY;
        documentId = VIKASAM_DOCUMENT;
        scorecardId = VIKASAM_SCORECARD;
        Fixture fx = fullyPinned();
        fx.category.setCode("VIKCAT001");
        fx.category.setName("Vikasan Bureau");
        fx.applicability.setBusinessStatus("APPROVED");
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isTrue();
        assertThat(r.configuration().customerCategoryId()).isEqualTo(VIKASAM_CATEGORY_ID);
        assertThat(r.configuration().workflowVersionId()).isEqualTo(VIKASAM_WORKFLOW_V1);
        assertThat(r.configuration().policyApplicabilityId()).isEqualTo(VIKASAM_APPLICABILITY);
        assertThat(r.configuration().policyDocumentId()).isEqualTo(VIKASAM_DOCUMENT);
        assertThat(r.configuration().scorecardId()).isEqualTo(VIKASAM_SCORECARD);
        verify(customerCategoryRepository, never()).save(any());
        verify(workflowConfigRepository, never()).save(any());
        verify(applicabilityRepository, never()).save(any());
        verify(policyDocumentRepository, never()).save(any());
        verify(scorecardRepository, never()).save(any());
    }

    @Test
    void classifierDistinguishesPartialAndInconsistent() {
        LoanApplication partial = baseApp();
        CustomerCategoryEntity catOnly = CustomerCategoryEntity.builder()
                .id(UUID.randomUUID())
                .code("PARTIAL")
                .versionNo(1)
                .name("Partial")
                .status(ConfigLifecycleStatus.ACTIVE)
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .inferenceNotes(new LinkedHashMap<>())
                .build();
        partial.setSelectedCustomerCategoryId(catOnly.getId());
        partial.setSelectedCustomerCategoryVersion(1);
        assertThat(CanonicalApplicationPinClassifier.classify(partial, catOnly, null, null))
                .isEqualTo(CanonicalApplicationPinClassifier.Class.PARTIALLY_PINNED);

        LoanApplication inconsistent = baseApp();
        inconsistent.setSelectedCustomerCategoryId(UUID.randomUUID());
        assertThat(CanonicalApplicationPinClassifier.classify(inconsistent, null, null, null))
                .isEqualTo(CanonicalApplicationPinClassifier.Class.INTERNALLY_INCONSISTENT);
    }

    @Test
    void ambiguousAuthoredDefinitionsDoNotPickLatest() {
        Fixture fx = fullyPinned();
        UUID graphId = UUID.randomUUID();
        CiPolicyRuleGraph graph = CiPolicyRuleGraph.builder()
                .id(graphId).policyDocumentId(documentId).documentVersion(1).graphHash("h").build();
        when(ruleGraphRepository.findByPolicyDocumentIdAndDocumentVersion(documentId, 1))
                .thenReturn(Optional.of(graph));
        when(operandRepository.findByGraphId(graphId)).thenReturn(List.of(
                CiPolicyRuleGraphOperand.builder()
                        .id(UUID.randomUUID()).graphId(graphId).nodeId(UUID.randomUUID())
                        .operandPath("r.x").originalToken("authored.dti")
                        .canonicalParameterId("authored.dti").resolutionStatus("RESOLVED").build()));
        when(calculationDefinitionRepository.findByCanonicalParameterId("authored.dti"))
                .thenReturn(List.of(
                        CiGacatDerivedCalculationDefinition.builder()
                                .id(UUID.randomUUID()).canonicalParameterId("authored.dti")
                                .calculationType("AUTHORED_EXPRESSION").status("TESTED").versionNo(2).build(),
                        CiGacatDerivedCalculationDefinition.builder()
                                .id(UUID.randomUUID()).canonicalParameterId("authored.dti")
                                .calculationType("AUTHORED_EXPRESSION").status("TESTED").versionNo(1).build()));
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.CALCULATION_DEFINITION_AMBIGUOUS.name());
    }

    @Test
    void policyLifecycleNotEligibleFailsExplicitly() {
        Fixture fx = fullyPinned();
        fx.applicability.setBusinessStatus("DRAFT");
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.POLICY_LIFECYCLE_NOT_ELIGIBLE.name());
        verify(applicabilityRepository, never()).findResolvableByTenant(any());
    }

    @Test
    void policyDocumentNotPinnedFailsExplicitly() {
        Fixture fx = fullyPinned();
        fx.app.setSelectedPolicyDocumentId(null);
        fx.applicability.setPolicyDocumentId(null);
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.POLICY_DOCUMENT_NOT_PINNED.name());
    }

    @Test
    void authoredCalculationNotPinnedFailsExplicitly() {
        Fixture fx = fullyPinned();
        UUID graphId = UUID.randomUUID();
        CiPolicyRuleGraph graph = CiPolicyRuleGraph.builder()
                .id(graphId).policyDocumentId(documentId).documentVersion(1).graphHash("h").build();
        when(ruleGraphRepository.findByPolicyDocumentIdAndDocumentVersion(documentId, 1))
                .thenReturn(Optional.of(graph));
        when(operandRepository.findByGraphId(graphId)).thenReturn(List.of(
                CiPolicyRuleGraphOperand.builder()
                        .id(UUID.randomUUID()).graphId(graphId).nodeId(UUID.randomUUID())
                        .operandPath("r.x").originalToken("authored.dti")
                        .canonicalParameterId("authored.dti").resolutionStatus("RESOLVED").build()));
        when(calculationDefinitionRepository.findByCanonicalParameterId("authored.dti"))
                .thenReturn(List.of());
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.AUTHORED_CALCULATION_NOT_PINNED.name());
        assertThat(r.configuration().calculationDefinitionPins())
                .anyMatch(p -> "authored.dti".equals(p.parameterId()) && p.fallbackRequired());
    }

    @Test
    void ambiguousBureauReportsDoNotChooseNewest() {
        Fixture fx = fullyPinned();
        fx.workflow.setBureauEnabled(true);
        CiBureauReport a = CiBureauReport.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).applicationId(fx.app.getId())
                .sourceRecordId(UUID.randomUUID()).subjectType("INDIVIDUAL").providerCode("EQUIFAX")
                .parserVersion("eqx-1").normalizerVersion("norm-1").qualityStatus("OK").build();
        CiBureauReport b = CiBureauReport.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).applicationId(fx.app.getId())
                .sourceRecordId(UUID.randomUUID()).subjectType("INDIVIDUAL").providerCode("EQUIFAX")
                .parserVersion("eqx-2").normalizerVersion("norm-2").qualityStatus("OK").build();
        when(bureauReportRepository.findByApplicationId(fx.app.getId())).thenReturn(List.of(a, b));
        CanonicalApplicationConfigurationResolution r = resolver.resolve(fx.app);
        assertThat(r.resolved()).isFalse();
        assertThat(r.reasonCodes()).contains(CanonicalResolutionFailureCode.BUREAU_REPORT_AMBIGUOUS.name());
        verify(bureauReportRepository, never()).findFirstByApplicationIdOrderByCreatedAtDesc(any());
    }

    private CanonicalApplicationConfigurationFreezeService freezeService(
            Map<UUID, CanonicalApplicationConfigurationEntity> store) {
        CanonicalApplicationConfigurationRepository repo = org.mockito.Mockito.mock(
                CanonicalApplicationConfigurationRepository.class);
        lenient().when(repo.findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(any(), any()))
                .thenAnswer(inv -> store.values().stream()
                        .filter(e -> e.getApplicationId().equals(inv.getArgument(0))
                                && inv.getArgument(1).equals(e.getStatus()))
                        .findFirst());
        lenient().when(repo.save(any())).thenAnswer(inv -> {
            CanonicalApplicationConfigurationEntity e = inv.getArgument(0);
            store.put(e.getId(), e);
            return e;
        });
        return new CanonicalApplicationConfigurationFreezeService(resolver, repo);
    }

    private Fixture fullyPinned() {
        CustomerCategoryEntity category = CustomerCategoryEntity.builder()
                .id(categoryId)
                .code("CC_TEST")
                .versionNo(1)
                .name("Test Category")
                .status(ConfigLifecycleStatus.ACTIVE)
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .policyApplicabilityId(applicabilityId)
                .policyDocumentId(documentId)
                .policyVersionLabel("v1")
                .workflowId(workflowId)
                .workflowVersion(1)
                .inferenceNotes(new LinkedHashMap<>())
                .build();
        WorkflowConfig workflow = WorkflowConfig.builder()
                .id(workflowId)
                .name("Test WF")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .version(1)
                .workflowFamilyId(workflowId)
                .publicationStatus("ACTIVE")
                .active(true)
                .bureauEnabled(false)
                .steps(List.of())
                .build();
        CiPolicyApplicability applicability = CiPolicyApplicability.builder()
                .id(applicabilityId)
                .tenantId(UUID.randomUUID())
                .policyDocumentId(documentId)
                .policyName("Test Policy")
                .policyVersionLabel("v1")
                .businessStatus("APPROVED")
                .products(new ArrayList<>(List.of("PERSONAL_LOAN")))
                .borrowerTypes(new ArrayList<>(List.of("INDIVIDUAL")))
                .build();
        CiPolicyDocument document = CiPolicyDocument.builder()
                .id(documentId)
                .tenantId(UUID.randomUUID())
                .name("Test Policy Doc")
                .contentHash("abc")
                .documentVersion(1)
                .scorecardId(scorecardId)
                .metadata(Map.of())
                .build();
        UnderwritingScorecard scorecard = UnderwritingScorecard.builder()
                .id(scorecardId)
                .name("Linked Scorecard")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .version(4)
                .priority(99)
                .active(false)
                .status("DRAFT")
                .scorecardJson(Map.of())
                .hardRulesJson(Map.of())
                .thresholdsJson(Map.of())
                .build();
        LoanApplication app = baseApp();
        app.setSelectedCustomerCategoryId(categoryId);
        app.setSelectedCustomerCategoryCode("CC_TEST");
        app.setSelectedCustomerCategoryVersion(1);
        app.setWorkflowId(workflowId);
        app.setWorkflowVersion(1);
        app.setSelectedPolicyApplicabilityId(applicabilityId);
        app.setSelectedPolicyDocumentId(documentId);
        app.setSelectedPolicyVersionLabel("v1");
        CiBureauReport report = CiBureauReport.builder()
                .id(bureauReportId)
                .tenantId(UUID.randomUUID())
                .applicationId(app.getId())
                .sourceRecordId(UUID.randomUUID())
                .subjectType("INDIVIDUAL")
                .providerCode("EQUIFAX")
                .parserVersion("eqx-1")
                .normalizerVersion("norm-1")
                .qualityStatus("OK")
                .build();
        when(customerCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(workflowConfigRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(applicabilityRepository.findById(applicabilityId)).thenReturn(Optional.of(applicability));
        when(policyDocumentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(scorecardRepository.findById(scorecardId)).thenReturn(Optional.of(scorecard));
        lenient().when(bureauReportRepository.findByApplicationId(app.getId())).thenReturn(List.of(report));
        return new Fixture(app, category, workflow, applicability, document, scorecard);
    }

    private LoanApplication baseApp() {
        LoanApplication app = new LoanApplication();
        app.setId(UUID.randomUUID());
        app.setBorrowerType(BorrowerType.INDIVIDUAL);
        app.setLoanProduct("PERSONAL_LOAN");
        app.setCreatedAt(Instant.parse("2026-08-01T06:30:00Z"));
        return app;
    }

    private record Fixture(
            LoanApplication app,
            CustomerCategoryEntity category,
            WorkflowConfig workflow,
            CiPolicyApplicability applicability,
            CiPolicyDocument document,
            UnderwritingScorecard scorecard) {}
}
