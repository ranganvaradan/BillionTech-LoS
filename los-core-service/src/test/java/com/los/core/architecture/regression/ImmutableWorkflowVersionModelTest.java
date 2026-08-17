package com.los.core.architecture.regression;

import com.los.core.customercategory.CategoryWorkflowBindService;
import com.los.core.customercategory.ConfigLifecycleStatus;
import com.los.core.customercategory.CustomerCategoryEntity;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.workflow.WorkflowContentHash;
import com.los.core.service.workflow.WorkflowEngineServiceImpl;
import com.los.core.audit.AdminConfigAuditSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * IMMUTABLE-WORKFLOW-VERSION-MODEL-1
 */
@ExtendWith(MockitoExtension.class)
class ImmutableWorkflowVersionModelTest {

    @Mock AdminConfigAuditSupport auditSupport;

    ConcurrentHashMap<UUID, WorkflowConfig> store;
    WorkflowConfigRepository repo;
    WorkflowEngineServiceImpl engine;
    CategoryWorkflowBindService bindService;

    UUID family = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    UUID v1Id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    UUID v2Id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    UUID v3Id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");

    @BeforeEach
    void setUp() {
        store = new ConcurrentHashMap<>();
        repo = org.mockito.Mockito.mock(WorkflowConfigRepository.class, invocation -> {
            String name = invocation.getMethod().getName();
            Object[] args = invocation.getArguments();
            if ("findById".equals(name)) {
                UUID id = (UUID) args[0];
                return Optional.ofNullable(store.get(id));
            }
            if ("findAll".equals(name)) {
                return new ArrayList<>(store.values());
            }
            if ("findByWorkflowFamilyIdOrderByVersionAsc".equals(name)) {
                UUID fam = (UUID) args[0];
                return store.values().stream()
                        .filter(w -> fam.equals(w.resolvedFamilyId()))
                        .sorted((a, b) -> Integer.compare(a.getVersion(), b.getVersion()))
                        .toList();
            }
            if ("save".equals(name)) {
                WorkflowConfig c = (WorkflowConfig) args[0];
                if (c.getId() == null) {
                    c.setId(UUID.randomUUID());
                }
                store.put(c.getId(), c);
                return c;
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        engine = new WorkflowEngineServiceImpl(repo, auditSupport);
        bindService = new CategoryWorkflowBindService(repo);
    }

    private WorkflowConfig version(UUID id, int ver, String status, boolean active, List<Map<String, Object>> steps) {
        WorkflowConfig c = WorkflowConfig.builder()
                .id(id)
                .name("Vikasam Business Loan")
                .borrowerType("INDIVIDUAL")
                .loanProduct("BUSINESS_TERM_LOAN")
                .intakeSegment("BORROWER")
                .steps(steps)
                .active(active)
                .version(ver)
                .workflowFamilyId(family)
                .publicationStatus(status)
                .build();
        store.put(id, c);
        return c;
    }

    private CustomerCategoryEntity categoryPinned(WorkflowConfig wf) {
        return CustomerCategoryEntity.builder()
                .id(UUID.randomUUID())
                .code("VIKCAT001")
                .versionNo(1)
                .name("Vikasan Bureau")
                .status(ConfigLifecycleStatus.APPROVED)
                .borrowerType("INDIVIDUAL")
                .loanProduct("BUSINESS_TERM_LOAN")
                .intakeSegment("BORROWER")
                .workflowId(wf.getId())
                .workflowVersion(wf.getVersion())
                .workflowContentHash(WorkflowContentHash.of(wf))
                .workflowName(wf.getName())
                .governanceJson(new LinkedHashMap<>())
                .build();
    }

    @Test
    void caseA_categoryPinnedToV1RemainsValidWhenV3Exists() {
        WorkflowConfig v1 = version(v1Id, 1, "SUPERSEDED", false, List.of(Map.of("stepKey", "PAN")));
        version(v2Id, 2, "SUPERSEDED", false, List.of(Map.of("stepKey", "PAN"), Map.of("stepKey", "AADHAAR")));
        version(v3Id, 3, "ACTIVE", true, List.of(Map.of("stepKey", "PAN"), Map.of("stepKey", "AADHAAR"), Map.of("stepKey", "Bank")));
        var checks = bindService.workflowActivationChecks(categoryPinned(v1));
        assertThat(checks).anyMatch(c -> "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code()) && c.ok());
        assertThat(checks).noneMatch(c -> c.detail() != null && c.detail().contains("current v3"));
        assertThat(categoryPinned(v1).getWorkflowId()).isEqualTo(v1Id);
    }

    @Test
    void caseB_editV2DoesNotChangeV1Content() {
        WorkflowConfig v1 = version(v1Id, 1, "SUPERSEDED", false, List.of(Map.of("stepKey", "PAN")));
        version(v2Id, 2, "DRAFT", false, List.of(Map.of("stepKey", "PAN"), Map.of("stepKey", "AADHAAR")));
        String v1Hash = WorkflowContentHash.of(v1);
        WorkflowConfigRequest req = new WorkflowConfigRequest();
        req.setName("Vikasam Business Loan");
        req.setBorrowerType(BorrowerType.INDIVIDUAL);
        req.setLoanProduct("BUSINESS_TERM_LOAN");
        req.setSteps(List.of(Map.of("stepKey", "CHANGED")));
        engine.updateWorkflow(v2Id, req);
        assertThat(WorkflowContentHash.of(store.get(v1Id))).isEqualTo(v1Hash);
        assertThat(store.get(v1Id).getSteps().get(0).get("stepKey")).isEqualTo("PAN");
    }

    @Test
    void caseC_v1AndV2RemainQueryableAfterV3() {
        version(v1Id, 1, "SUPERSEDED", false, List.of(Map.of("stepKey", "PAN")));
        version(v2Id, 2, "SUPERSEDED", false, List.of(Map.of("stepKey", "AADHAAR")));
        version(v3Id, 3, "ACTIVE", true, List.of(Map.of("stepKey", "Bank")));
        assertThat(repo.findById(v1Id)).isPresent();
        assertThat(repo.findById(v2Id)).isPresent();
        assertThat(repo.findById(v3Id)).isPresent();
        assertThat(repo.findByWorkflowFamilyIdOrderByVersionAsc(family)).hasSize(3);
    }

    @Test
    void caseD_activationValidatesExactBoundVersionNotLatest() {
        WorkflowConfig v1 = version(v1Id, 1, "SUPERSEDED", false, List.of(Map.of("stepKey", "PAN")));
        version(v3Id, 3, "ACTIVE", true, List.of(Map.of("stepKey", "Bank")));
        var checks = bindService.workflowActivationChecks(categoryPinned(v1));
        var identity = checks.stream().filter(c -> "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code())).findFirst().orElseThrow();
        assertThat(identity.ok()).isTrue();
        assertThat(identity.detail()).contains("exactVersionId=" + v1Id);
        assertThat(identity.detail()).doesNotContain("current v3");
    }

    @Test
    void caseE_newCategoryVersionCanBindV3WithoutMovingV1Category() {
        WorkflowConfig v1 = version(v1Id, 1, "SUPERSEDED", false, List.of(Map.of("stepKey", "PAN")));
        WorkflowConfig v3 = version(v3Id, 3, "ACTIVE", true, List.of(Map.of("stepKey", "Bank")));
        CustomerCategoryEntity cat1 = categoryPinned(v1);
        CustomerCategoryEntity cat2 = categoryPinned(v3);
        cat2.setVersionNo(2);
        assertThat(cat1.getWorkflowId()).isEqualTo(v1Id);
        assertThat(cat2.getWorkflowId()).isEqualTo(v3Id);
        assertThat(bindService.workflowActivationChecks(cat1).stream()
                .anyMatch(c -> "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code()) && c.ok())).isTrue();
        assertThat(bindService.workflowActivationChecks(cat2).stream()
                .anyMatch(c -> "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code()) && c.ok())).isTrue();
    }

    @Test
    void caseG_versionNumbersMayRepeatAcrossFamilies() {
        UUID otherFamily = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        version(v1Id, 1, "ACTIVE", true, List.of(Map.of("stepKey", "PAN")));
        WorkflowConfig other = WorkflowConfig.builder()
                .id(otherFamily)
                .name("Other journey")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .steps(List.of())
                .active(true)
                .version(1)
                .workflowFamilyId(otherFamily)
                .publicationStatus("ACTIVE")
                .build();
        store.put(otherFamily, other);
        assertThat(store.get(v1Id).getVersion()).isEqualTo(store.get(otherFamily).getVersion());
        assertThat(store.get(v1Id).getId()).isNotEqualTo(store.get(otherFamily).getId());
    }

    @Test
    void caseF_supersedingV1DoesNotCorruptCategoryReadability() {
        WorkflowConfig v1 = version(v1Id, 1, "SUPERSEDED", false, List.of(Map.of("stepKey", "PAN")));
        version(v3Id, 3, "ACTIVE", true, List.of(Map.of("stepKey", "Bank")));
        String hash = WorkflowContentHash.of(v1);
        CustomerCategoryEntity cat = categoryPinned(v1);
        engine.deactivateWorkflow(v1Id);
        assertThat(store.get(v1Id).getSteps().get(0).get("stepKey")).isEqualTo("PAN");
        assertThat(WorkflowContentHash.of(store.get(v1Id))).isEqualTo(hash);
        assertThat(cat.getWorkflowId()).isEqualTo(v1Id);
        assertThat(bindService.workflowActivationChecks(cat)).anyMatch(
                c -> "WORKFLOW_CONTENT_IDENTITY_VALID".equals(c.code()) && c.ok());
        assertThat(repo.findById(v1Id)).isPresent();
    }

    @Test
    void createNewVersion_newIdSameFamilyDoesNotMutateSource() {
        version(v1Id, 1, "ACTIVE", true, List.of(Map.of("stepKey", "PAN")));
        String v1Hash = WorkflowContentHash.of(store.get(v1Id));
        WorkflowConfigRequest req = new WorkflowConfigRequest();
        req.setName("Vikasam Business Loan");
        req.setBorrowerType(BorrowerType.INDIVIDUAL);
        req.setLoanProduct("BUSINESS_TERM_LOAN");
        req.setSteps(List.of(Map.of("stepKey", "AADHAAR")));
        var created = engine.createNewVersion(v1Id, req);
        assertThat(created.getId()).isNotEqualTo(v1Id);
        assertThat(created.getWorkflowFamilyId()).isEqualTo(family);
        assertThat(created.getVersion()).isEqualTo(2);
        assertThat(created.getPublicationStatus()).isEqualTo("DRAFT");
        assertThat(store.get(v1Id).getVersion()).isEqualTo(1);
        assertThat(WorkflowContentHash.of(store.get(v1Id))).isEqualTo(v1Hash);
        assertThat(store.get(v1Id).getSteps().get(0).get("stepKey")).isEqualTo("PAN");
    }

    @Test
    void activeVersionCannotBeUpdatedInPlace() {
        version(v1Id, 1, "ACTIVE", true, List.of(Map.of("stepKey", "PAN")));
        WorkflowConfigRequest req = new WorkflowConfigRequest();
        req.setName("x");
        req.setBorrowerType(BorrowerType.INDIVIDUAL);
        req.setLoanProduct("BUSINESS_TERM_LOAN");
        req.setSteps(List.of(Map.of("stepKey", "CHANGED")));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> engine.updateWorkflow(v1Id, req));
        assertThat(ex.getReason()).isEqualTo("WORKFLOW_VERSION_IMMUTABLE");
        assertThat(store.get(v1Id).getSteps().get(0).get("stepKey")).isEqualTo("PAN");
    }

    @Test
    void sourceGuards_noInPlaceActiveVersionIncrement() throws Exception {
        Path root = Path.of("src/main/java/com/los/core");
        String engine = Files.readString(root.resolve("service/workflow/WorkflowEngineServiceImpl.java"));
        assertThat(engine).contains("WORKFLOW_VERSION_IMMUTABLE");
        assertThat(engine).contains("createNewVersion");
        assertThat(engine).doesNotContain("setVersion(config.getVersion() + 1)");
        String bind = Files.readString(root.resolve("customercategory/CategoryWorkflowBindService.java"));
        assertThat(bind).contains("exactVersionId=");
        assertThat(bind).doesNotContain("stored v\" + e.getWorkflowVersion()");
        assertThat(bind).doesNotContain("findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc");
        Path uiRoot = Path.of("../ui-service/src");
        String catPage = Files.readString(uiRoot.resolve("pages/CustomerCategoriesPage.tsx"));
        assertThat(catPage).contains("workflowPickerLabel");
        assertThat(catPage).contains("linkedWorkflowVersion");
        assertThat(catPage).contains("eligibleForNewBind");
        assertThat(catPage).doesNotContain("latest sibling");
        String wfPage = Files.readString(uiRoot.resolve("pages/WorkflowsPage.tsx"));
        assertThat(wfPage).contains("createNewWorkflowVersion");
        assertThat(wfPage).contains("Create New Version");
        assertThat(wfPage).doesNotContain("setVersion(config.getVersion() + 1)");
        String hashSrc = Files.readString(root.resolve("service/workflow/WorkflowContentHash.java"));
        assertThat(hashSrc).contains("basis.put(\"id\"");
        assertThat(hashSrc).contains("basis.put(\"version\"");
        try (Stream<Path> walk = Files.walk(root)) {
            boolean latestResolver = walk.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try { return Files.readString(p); } catch (Exception e) { return ""; }
                    })
                    .anyMatch(s -> s.contains("latest workflow family version") && s.contains("workflowActivationChecks"));
            assertThat(latestResolver).isFalse();
        }
    }
}
