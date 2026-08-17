package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyReview;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyLifecycleServiceTest {

    private PolicyLifecycleService lifecycle;
    private PolicyStudioSession session;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCutover().setAllowCanonicalAuthority(false);
        lifecycle = new PolicyLifecycleService(new PolicyApplicabilityResolver(), null, props, null);
        lifecycle.clearCatalogueForTests(tenantId);

        session = new PolicyStudioSession();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Banking BRE")
                .documentType("TXT")
                .contentHash("abc")
                .status(DocumentStatus.DRAFT_READY.name())
                .documentVersion(1)
                .sourceText("DIGILEAP banking policy sample")
                .productScope("DIGILEAP")
                .uploadedBy("staging")
                .metadata(new LinkedHashMap<>())
                .build();
        session.setDocument(doc);

        session.getInterpretations().add(CiPolicyInterpretation.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .naturalLanguageMeaning("Eligibility rule interpreted")
                .build());
        session.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("R1")
                .expression(Map.of("op", "true"))
                .reviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name())
                .build());
        session.getTestCases().add(CiPolicyTestCase.builder()
                .id(UUID.randomUUID())
                .name("Boundary")
                .expectedOutcome("PASS")
                .reviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name())
                .build());
        session.setSimulation(new LinkedHashMap<>(Map.of(
                "runId", "sim-1",
                "simulationReviewed", true)));
        session.getReviews().add(CiPolicyReview.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .policyDocumentId(doc.getId())
                .subjectType("DOCUMENT")
                .subjectId(doc.getId())
                .reviewState(ReviewState.CREDIT_MANAGER_APPROVED.name())
                .reviewer("credit_manager")
                .createdAt(Instant.now())
                .build());
        session.getReviews().add(CiPolicyReview.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .policyDocumentId(doc.getId())
                .subjectType("DOCUMENT")
                .subjectId(doc.getId())
                .reviewState(ReviewState.CHECKER_APPROVED.name())
                .reviewer("policy_checker")
                .createdAt(Instant.now())
                .build());
    }

    @Test
    void saveDraft_andSubmitForReview() {
        Map<String, Object> saved = lifecycle.saveDraft(session, Map.of(
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-09-01",
                "reasonForChange", "Initial DigiLeap schedule"));
        assertThat(saved.get("businessStatus")).isEqualTo(PolicyBusinessLifecycleStatus.DRAFT);
        assertThat(saved.get("contentEditable")).isEqualTo(true);
        assertThat(saved.get("lifecycleAuthority")).isEqualTo(PolicyCanonicalLifecycleAuthority.NAME);
        assertThat(saved.get("allowCanonicalAuthority")).isEqualTo(false);

        Map<String, Object> submitted = lifecycle.submitForReview(session, Map.of());
        assertThat(submitted.get("businessStatus")).isEqualTo(PolicyBusinessLifecycleStatus.IN_REVIEW);
    }

    @Test
    void criticalDataReadiness_blocksApproval() {
        // C — EDI is MANUAL authorised and no longer blocks. CASE F: NOT_READY calculation operand
        // on a new DRAFT must block Approve via PolicyExecutionReadiness current parameter blockers.
        lifecycle.saveDraft(session, Map.of(
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-04-01"));
        session.getRuleCandidates().clear();
        session.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CM_BUREAU_CC_OVERDUE_AMOUNT_GTE")
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", "bureau.cc_overdue_amount"),
                        "right", Map.of("const", 0)))
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessTitle", "Credit-card overdue amount",
                        "disposition", "ACCEPTED",
                        "parameterId", "bureau.cc_overdue_amount")))
                .build());
        assertThatThrownBy(() -> lifecycle.approvePolicy(session, Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("overdue");
    }

    @Test
    void scheduleAfterApproved_registersShadowResolution_authorityDisabled() {
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("policyVersion", "v1");
        life.put("policyType", "BANKING_POLICY");
        life.put("businessStatus", PolicyBusinessLifecycleStatus.APPROVED);
        life.put("approvedBy", "credit_manager");
        life.put("checker", "policy_checker");
        life.put("createdBy", "staging");
        life.put("contentImmutable", true);
        life.put("applicability", new LinkedHashMap<>(Map.of(
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-04-01",
                "effectiveUntil", "2026-08-31")));
        session.getDocument().setMetadata(new LinkedHashMap<>(Map.of(
                PolicyLifecycleService.META_KEY, life)));

        Map<String, Object> scheduled = lifecycle.schedulePolicy(session, Map.of(
                "effectiveFrom", "2026-04-01",
                "effectiveUntil", "2026-08-31",
                "products", List.of("DIGILEAP"),
                "businessDate", "2026-08-25"));

        assertThat(List.of(PolicyBusinessLifecycleStatus.ACTIVE, PolicyBusinessLifecycleStatus.SCHEDULED))
                .contains(String.valueOf(scheduled.get("businessStatus")));
        assertThat(scheduled.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(scheduled.get("productionAuthority")).isEqualTo("DISABLED");

        Map<String, Object> shadow = lifecycle.resolveShadowApplication(tenantId, Map.of(
                "applicationCode", "APP-X",
                "productCode", "DIGILEAP",
                "evaluationDate", "2026-08-25"));
        assertThat(shadow.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(shadow.get("banner")).isEqualTo("ONE POLICY SELECTED");
    }

    @Test
    void materialEdit_blockedOnApproved_requiresNewVersion() {
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("policyVersion", "v1");
        life.put("businessStatus", PolicyBusinessLifecycleStatus.APPROVED);
        life.put("contentImmutable", true);
        life.put("applicability", Map.of("products", List.of("DIGILEAP")));
        session.getDocument().setMetadata(new LinkedHashMap<>(Map.of(PolicyLifecycleService.META_KEY, life)));

        Map<String, Object> gate = lifecycle.assertImmutability(session, Map.of("edit", "threshold"));
        assertThat(gate.get("allowed")).isEqualTo(false);
        assertThat(gate.get("requiresNewVersion")).isEqualTo(true);

        assertThatThrownBy(() -> lifecycle.saveDraft(session, Map.of("products", List.of("DIGILEAP"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void overlappingSchedule_blocked() {
        UUID otherId = UUID.randomUUID();
        lifecycle.registerForTests(tenantId, new PolicyApplicabilityRecord(
                otherId, "DigiLeap Policy", "v3", "BANKING_POLICY",
                PolicyBusinessLifecycleStatus.ACTIVE, List.of("DIGILEAP"),
                null, null, null, List.of(), null, null, null, null,
                LocalDate.of(2026, 9, 1), null,
                null, null, "cm", "ck", "au", false, Map.of()));

        Map<String, Object> life = new LinkedHashMap<>();
        life.put("policyVersion", "v4");
        life.put("businessStatus", PolicyBusinessLifecycleStatus.APPROVED);
        life.put("approvedBy", "cm");
        life.put("checker", "ck");
        life.put("createdBy", "au");
        life.put("applicability", Map.of(
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-09-15"));
        session.getDocument().setMetadata(new LinkedHashMap<>(Map.of(PolicyLifecycleService.META_KEY, life)));

        assertThatThrownBy(() -> lifecycle.schedulePolicy(session, Map.of(
                "effectiveFrom", "2026-09-15",
                "products", List.of("DIGILEAP"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DIGILEAP");
    }

    @Test
    void settingsView_exposesBusinessHeaderWithoutTechnicalIdsByDefault() {
        Map<String, Object> view = lifecycle.settingsView(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) view.get("policySettings");
        assertThat(settings).containsKeys("policyName", "policyVersion", "products", "status");
        assertThat(settings).doesNotContainKey("contentHash");
        assertThat(view.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void submitForReview_blockedWithoutUnderwritingRules() {
        session.getRuleCandidates().clear();
        lifecycle.saveDraft(session, Map.of(
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-09-01"));
        assertThatThrownBy(() -> lifecycle.submitForReview(session, Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("underwriting rule");
    }

    @Test
    void settingsView_cmPrimaryAction_submitWhenDraftReady() {
        lifecycle.saveDraft(session, Map.of(
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-09-01"));
        Map<String, Object> view = lifecycle.settingsView(session);
        assertThat(view.get("progressCurrent")).isEqualTo("DRAFT");
        @SuppressWarnings("unchecked")
        Map<String, Object> primary = (Map<String, Object>) view.get("primaryAction");
        assertThat(primary.get("code")).isEqualTo("SUBMIT_FOR_REVIEW");
        assertThat(primary.get("enabled")).isEqualTo(true);
        assertThat(view.get("readinessItems")).asList().isNotEmpty();
        assertThat(view.get("approvals")).isInstanceOf(Map.class);
    }

    @Test
    void settingsView_approved_primaryIsSchedule() {
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("policyVersion", "v1");
        life.put("businessStatus", PolicyBusinessLifecycleStatus.APPROVED);
        life.put("contentImmutable", true);
        life.put("applicability", new LinkedHashMap<>(Map.of(
                "products", List.of("DIGILEAP"),
                "effectiveFrom", "2026-04-01")));
        session.getDocument().setMetadata(new LinkedHashMap<>(Map.of(PolicyLifecycleService.META_KEY, life)));

        Map<String, Object> view = lifecycle.settingsView(session);
        assertThat(view.get("progressCurrent")).isEqualTo("APPROVED");
        @SuppressWarnings("unchecked")
        Map<String, Object> primary = (Map<String, Object>) view.get("primaryAction");
        assertThat(primary.get("code")).isEqualTo("SCHEDULE_POLICY");
        assertThat(primary.get("enabled")).isEqualTo(true);
        assertThat(view.get("shadowBoundary")).isInstanceOf(Map.class);
    }
}
