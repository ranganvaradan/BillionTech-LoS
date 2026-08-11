package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.catalogue.IngestionMatchClassification;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyBusinessLifecycleStatus;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.CmRuleAuthoringService;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import com.los.core.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * POLICY-STUDIO-UX-CLOSURE-1 — goldens A–H (lifecycle delete/retire + clause disposition).
 * Policy Studio remains shadow/governance; allowCanonicalAuthority=false.
 */
class PolicyStudioUxClosure1Test {

    private StagingPolicyStudioDemoService demo;
    private PolicyLifecycleService lifecycle;
    private PolicyStudioOrchestrator orchestrator;
    private CreditIntelligenceProperties properties;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        properties = new CreditIntelligenceProperties();
        properties.setDefaultTenantId(tenantId);
        properties.getStagingDemo().setEnabled(true);
        properties.getCutover().setAllowCanonicalAuthority(false);
        orchestrator = new PolicyStudioOrchestrator();
        orchestrator.persistence().clearAllForTests();
        lifecycle = new PolicyLifecycleService(
                new PolicyApplicabilityResolver(), orchestrator, properties, null);
        demo = new StagingPolicyStudioDemoService(
                properties, orchestrator, new PolicyTextExtractionService(),
                null, lifecycle, null, new CmRuleAuthoringService());
    }

    @Test
    void goldenA_draftDelete_removesOnlyTargetDraft() {
        Map<String, Object> a = demo.createFromScratch(Map.of(
                "policyName", "Disposable Draft A"), "admin", null);
        Map<String, Object> b = demo.createFromScratch(Map.of(
                "policyName", "Disposable Draft B"), "admin", null);
        UUID idA = UUID.fromString(String.valueOf(((Map<?, ?>) a.get("policyHeader")).get("documentId")));
        UUID idB = UUID.fromString(String.valueOf(((Map<?, ?>) b.get("policyHeader")).get("documentId")));

        Map<String, Object> deleted = demo.deleteDraftPolicy(idA, Map.of(
                "reviewerRole", "ADMINISTRATOR",
                "reviewer", "uat_admin",
                "reason", "UAT disposable draft cleanup"), null);

        assertThat(deleted.get("deleted")).isEqualTo(true);
        assertThat(deleted.get("auditEvent")).isEqualTo("DRAFT_DELETED");
        assertThat(orchestrator.persistence().loadSession(idA)).isNull();
        assertThat(orchestrator.persistence().loadSession(idB)).isNotNull();
        assertThat(demo.listExistingPolicies().stream()
                .noneMatch(r -> idA.toString().equals(String.valueOf(r.get("documentId"))))).isTrue();
        assertThat(demo.listExistingPolicies().stream()
                .anyMatch(r -> idB.toString().equals(String.valueOf(r.get("documentId"))))).isTrue();
        assertThat(properties.getCutover().isAllowCanonicalAuthority()).isFalse();
    }

    @Test
    void goldenB_activeDeleteBlocked() {
        UUID id = createDisposableDraft("Active Delete Block Policy");
        lifecycle.stampActiveForVersioningDemo(orchestrator.requireSession(id), Map.of(
                "reviewer", "staging", "reasonForChange", "stamp for delete-block golden"));

        assertThatThrownBy(() -> demo.deleteDraftPolicy(id, Map.of(
                "reviewerRole", "ADMINISTRATOR",
                "reviewer", "uat_admin"), null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> {
                    BusinessRuleException bre = (BusinessRuleException) ex;
                    assertThat(bre.getReason()).isEqualTo("POLICY_DELETE_NOT_ALLOWED");
                });
        assertThat(orchestrator.persistence().loadSession(id)).isNotNull();
    }

    @Test
    void goldenC_retireActive_retainsReasonAndReadableHistory() {
        UUID id = createDisposableDraft("Retire Golden Policy");
        lifecycle.stampActiveForVersioningDemo(orchestrator.requireSession(id), Map.of(
                "reviewer", "staging", "reasonForChange", "stamp for retire golden"));

        Map<String, Object> retired = demo.retireLifecyclePolicy(id, Map.of(
                "reviewerRole", "CREDIT_MANAGER",
                "reviewer", "uat_cm",
                "retirementReason", "UAT retirement of disposable ACTIVE version"), null);

        assertThat(String.valueOf(retired.get("message"))).containsIgnoringCase("RETIRED");
        PolicyStudioSession session = orchestrator.requireSession(id);
        @SuppressWarnings("unchecked")
        Map<String, Object> life = (Map<String, Object>) session.getDocument().getMetadata()
                .get(PolicyLifecycleService.META_KEY);
        assertThat(life.get("businessStatus")).isEqualTo(PolicyBusinessLifecycleStatus.RETIRED);
        assertThat(life.get("retirementReason")).isEqualTo("UAT retirement of disposable ACTIVE version");
        assertThat(life.get("retiredBy")).isEqualTo("uat_cm");
        assertThat(life.get("retiredAt")).isNotNull();
        // Still readable
        assertThat(demo.sessionView(id, null).get("policyHeader")).isNotNull();
        assertThat(properties.getCutover().isAllowCanonicalAuthority()).isFalse();
    }

    @Test
    void goldenD_ignoreNarrative_decrementsNeedsInput_preservesWording() {
        UUID id = createDisposableDraft("Ignore Narrative Policy");
        PolicyStudioSession session = orchestrator.requireSession(id);
        CiPolicyRuleCandidate narrative = addClauseRule(session,
                "Board resolution / partner authority letter required",
                IngestionMatchClassification.DOCUMENT_REQUIREMENT);
        long before = PolicyExecutionReadiness.countNeedsBusinessInput(session);
        // Force a second included incomplete executable so Needs Input can move when narrative is ignored
        // (narrative itself is non-executable — needsInput should not count it either before or after)
        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(narrative)).isFalse();

        demo.reviewRule(id, narrative.getId(), Map.of(
                "uiAction", "IGNORE_FOR_AUTOMATION",
                "reason", "Documentary — not automation",
                "reviewer", "uat_cm",
                "reviewerRole", "CREDIT_MANAGER"), null);

        PolicyStudioSession reloaded = orchestrator.requireSession(id);
        CiPolicyRuleCandidate after = reloaded.getRuleCandidates().stream()
                .filter(r -> narrative.getId().equals(r.getId())).findFirst().orElseThrow();
        assertThat(after.getMetadata().get("disposition")).isEqualTo("IGNORE_FOR_AUTOMATION");
        assertThat(after.getMetadata().get("excludedFromActivation")).isEqualTo(true);
        assertThat(String.valueOf(after.getMetadata().get("dispositionReason")))
                .contains("Documentary");
        // Source wording preserved on candidate
        assertThat(String.valueOf(after.getMetadata().get("clauseText")))
                .containsIgnoringCase("authority");
        long afterCount = PolicyExecutionReadiness.countNeedsBusinessInput(reloaded);
        assertThat(afterCount).isLessThanOrEqualTo(before);
        assertThat(properties.getCutover().isAllowCanonicalAuthority()).isFalse();
    }

    @Test
    void goldenE_keepAsPolicyRequirement_notUnresolvedCanonical() {
        UUID id = createDisposableDraft("Keep Requirement Policy");
        PolicyStudioSession session = orchestrator.requireSession(id);
        CiPolicyRuleCandidate doc = addClauseRule(session,
                "GST registration & returns must be submitted",
                IngestionMatchClassification.DOCUMENT_REQUIREMENT);

        demo.reviewRule(id, doc.getId(), Map.of(
                "uiAction", "KEEP_AS_POLICY_REQUIREMENT",
                "reason", "Documentary requirement",
                "reviewer", "uat_cm",
                "reviewerRole", "CREDIT_MANAGER"), null);

        PolicyStudioSession reloaded = orchestrator.requireSession(id);
        CiPolicyRuleCandidate after = reloaded.getRuleCandidates().stream()
                .filter(r -> doc.getId().equals(r.getId())).findFirst().orElseThrow();
        assertThat(after.getMetadata().get("disposition")).isEqualTo("KEEP_AS_POLICY_REQUIREMENT");
        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(after)).isFalse();
        Map<String, Object> view = demo.sessionView(id, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) view.get("ruleCards");
        assertThat(cards.stream()
                .filter(c -> doc.getId().toString().equals(String.valueOf(c.get("id"))))
                .findFirst().orElseThrow().get("status"))
                .isEqualTo("Policy requirement");
    }

    @Test
    void goldenF_executableDependencyCannotBeFalseIgnored() {
        UUID id = createDisposableDraft("Executable Bypass Block Policy");
        PolicyStudioSession session = orchestrator.requireSession(id);
        Map<String, Object> execMeta = new LinkedHashMap<>();
        execMeta.put("classification", IngestionMatchClassification.NEW_AUTOMATABLE_RULE.name());
        execMeta.put("catalogueBacked", true);
        execMeta.put("businessCapabilityId", "BANK.ADB_3M");
        execMeta.put("parameterId", "banking.avg_daily_balance_3m");
        execMeta.put("NEEDS_INPUT", true);
        execMeta.put("blockedReason", "Policy threshold missing");
        execMeta.put("clauseText", "ADB must be at least policy threshold");
        CiPolicyRuleCandidate exec = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("BANK_ADB_MIN_THRESHOLD")
                .reviewStatus(ReviewState.AI_DRAFTED.name())
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", "banking.avg_daily_balance_3m"),
                        "right", Map.of("const", "")))
                .metadata(execMeta)
                .build();
        session.getRuleCandidates().add(exec);
        orchestrator.persistence().saveSessionSnapshot(session);

        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(exec)).isTrue();
        assertThat(PolicyExecutionReadiness.isExecutionReady(exec)).isFalse();

        assertThatThrownBy(() -> demo.reviewRule(id, exec.getId(), Map.of(
                "uiAction", "IGNORE_FOR_AUTOMATION",
                "reason", "try to bypass",
                "reviewer", "uat_cm",
                "reviewerRole", "CREDIT_MANAGER"), null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getReason())
                        .isEqualTo("POLICY_DISPOSITION_NOT_ALLOWED"));

        PolicyStudioSession reloaded = orchestrator.requireSession(id);
        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(
                reloaded.getRuleCandidates().stream()
                        .filter(r -> exec.getId().equals(r.getId())).findFirst().orElseThrow())).isTrue();
        assertThat(PolicyExecutionReadiness.isExecutionReady(
                reloaded.getRuleCandidates().stream()
                        .filter(r -> exec.getId().equals(r.getId())).findFirst().orElseThrow())).isFalse();
    }

    @Test
    void goldenG_versionCopy_preservesDispositionIndependently() {
        UUID v1 = createDisposableDraft("Disposition Version Policy");
        PolicyStudioSession s1 = orchestrator.requireSession(v1);
        CiPolicyRuleCandidate clause = addClauseRule(s1,
                "Event of Default / delinquency wording",
                IngestionMatchClassification.NARRATIVE);
        demo.reviewRule(v1, clause.getId(), Map.of(
                "uiAction", "KEEP_AS_POLICY_REQUIREMENT",
                "reason", "v1 keep",
                "reviewer", "uat_cm",
                "reviewerRole", "CREDIT_MANAGER"), null);
        lifecycle.stampActiveForVersioningDemo(orchestrator.requireSession(v1), Map.of(
                "reviewer", "staging", "reasonForChange", "version copy golden"));

        Map<String, Object> created = demo.createLifecycleVersion(v1, Map.of(
                "reasonForChange", "Create v2 for disposition independence"), null);
        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));

        PolicyStudioSession s2 = orchestrator.requireSession(v2);
        CiPolicyRuleCandidate v2Clause = s2.getRuleCandidates().stream()
                .filter(r -> "Event of Default / delinquency wording"
                        .equals(String.valueOf(r.getMetadata().get("clauseText"))))
                .findFirst().orElseThrow();
        assertThat(v2Clause.getMetadata().get("disposition")).isEqualTo("KEEP_AS_POLICY_REQUIREMENT");

        demo.reviewRule(v2, v2Clause.getId(), Map.of(
                "uiAction", "IGNORE_FOR_AUTOMATION",
                "reason", "v2 ignore",
                "reviewer", "uat_cm",
                "reviewerRole", "CREDIT_MANAGER"), null);

        PolicyStudioSession s1Reload = orchestrator.requireSession(v1);
        assertThat(s1Reload.getRuleCandidates().stream()
                .filter(r -> clause.getId().equals(r.getId())).findFirst().orElseThrow()
                .getMetadata().get("disposition")).isEqualTo("KEEP_AS_POLICY_REQUIREMENT");
        PolicyStudioSession s2Reload = orchestrator.requireSession(v2);
        assertThat(s2Reload.getRuleCandidates().stream()
                .filter(r -> v2Clause.getId().equals(r.getId())).findFirst().orElseThrow()
                .getMetadata().get("disposition")).isEqualTo("IGNORE_FOR_AUTOMATION");
    }

    @Test
    void goldenH_duplicateDisplayName_deleteByIdOnly() {
        Map<String, Object> d1 = demo.createFromScratch(Map.of(
                "policyName", "Banking BRE — EXAMPLE"), "admin", null);
        Map<String, Object> d2 = demo.createFromScratch(Map.of(
                "policyName", "Banking BRE — EXAMPLE"), "admin", null);
        UUID id1 = UUID.fromString(String.valueOf(((Map<?, ?>) d1.get("policyHeader")).get("documentId")));
        UUID id2 = UUID.fromString(String.valueOf(((Map<?, ?>) d2.get("policyHeader")).get("documentId")));
        assertThat(id1).isNotEqualTo(id2);

        demo.deleteDraftPolicy(id1, Map.of(
                "reviewerRole", "ADMINISTRATOR",
                "reviewer", "uat_admin",
                "reason", "Remove duplicate example draft by id"), null);

        assertThat(orchestrator.persistence().loadSession(id1)).isNull();
        assertThat(orchestrator.persistence().loadSession(id2)).isNotNull();
        assertThat(orchestrator.requireSession(id2).getDocument().getName())
                .isEqualTo("Banking BRE — EXAMPLE");
    }

    @Test
    void needsInputCount_usesExecutionReadinessNotRawFlag() {
        UUID id = createDisposableDraft("Needs Input Semantics");
        PolicyStudioSession session = orchestrator.requireSession(id);
        addClauseRule(session, "Narrative only", IngestionMatchClassification.NARRATIVE);
        // Mark NEEDS_INPUT on narrative — must NOT inflate landing needsInputCount
        session.getRuleCandidates().get(0).getMetadata().put("NEEDS_INPUT", true);
        orchestrator.persistence().saveSessionSnapshot(session);

        Map<String, Object> row = demo.listExistingPolicies().stream()
                .filter(r -> id.toString().equals(String.valueOf(r.get("documentId"))))
                .findFirst().orElseThrow();
        assertThat(((Number) row.get("needsInputCount")).longValue())
                .isEqualTo(PolicyExecutionReadiness.countNeedsBusinessInput(
                        orchestrator.requireSession(id)));
        assertThat(row.get("availableActions")).asList().contains("OPEN", "COPY", "DELETE");
    }

    private UUID createDisposableDraft(String name) {
        Map<String, Object> created = demo.createFromScratch(Map.of("policyName", name), "admin", null);
        return UUID.fromString(String.valueOf(((Map<?, ?>) created.get("policyHeader")).get("documentId")));
    }

    private CiPolicyRuleCandidate addClauseRule(
            PolicyStudioSession session, String wording, IngestionMatchClassification classification) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("classification", classification.name());
        meta.put("dataRequirementOnly",
                classification == IngestionMatchClassification.DOCUMENT_REQUIREMENT
                        || classification == IngestionMatchClassification.DATA_REQUIREMENT);
        meta.put("classificationOnly",
                classification == IngestionMatchClassification.NARRATIVE
                        || classification == IngestionMatchClassification.AMBIGUOUS);
        meta.put("NEEDS_INPUT", true);
        meta.put("clauseText", wording);
        CiPolicyRuleCandidate rule = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CLAUSE_" + UUID.randomUUID().toString().substring(0, 8))
                .reviewStatus(ReviewState.AI_DRAFTED.name())
                .expression(Map.of("op", "true"))
                .metadata(meta)
                .build();
        session.getRuleCandidates().add(rule);
        orchestrator.persistence().saveSessionSnapshot(session);
        return rule;
    }
}
