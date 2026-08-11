package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyReview;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyBusinessLifecycleStatus;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.lifecycle.StructuredPolicySessionCloner;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.CmRuleAuthoringService;
import com.los.core.creditintelligence.policystudio.parameters.ParameterResolutionSupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-VERSION-INTEGRITY-1 — Create New Version must structured-clone, not re-ingest.
 */
class PolicyVersionIntegrity1Test {

    private StagingPolicyStudioDemoService demoService;
    private PolicyLifecycleService lifecycle;
    private PolicyStudioOrchestrator orchestrator;
    private CreditIntelligenceProperties properties;
    private CmRuleAuthoringService authoring;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        properties = new CreditIntelligenceProperties();
        properties.setDefaultTenantId(tenantId);
        properties.getStagingDemo().setEnabled(true);
        properties.getCutover().setAllowCanonicalAuthority(false);
        orchestrator = new PolicyStudioOrchestrator();
        lifecycle = new PolicyLifecycleService(
                new PolicyApplicabilityResolver(), orchestrator, properties, null);
        authoring = new CmRuleAuthoringService();
        demoService = new StagingPolicyStudioDemoService(
                properties, orchestrator, new PolicyTextExtractionService(),
                null, lifecycle, null, authoring);
    }

    @Test
    void newVersion_clonesStructuredUwRules_withoutReingest() {
        UUID v1 = buildApprovedSimplePolicy();
        Map<String, Object> created = demoService.createLifecycleVersion(
                v1, Map.of("reasonForChange", "Material change"), null);

        assertThat(created.get("structuredClone")).isEqualTo(true);
        assertThat(created.get("reingested")).isEqualTo(false);
        assertThat(((Number) created.get("underwritingRuleCount")).longValue()).isEqualTo(5);

        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));
        PolicyStudioSession s2 = orchestrator.requireSession(v2);
        assertThat(StructuredPolicySessionCloner.countUnderwritingRules(s2)).isEqualTo(5);
        assertThat(s2.getDocument().getMetadata().get("reingested")).isEqualTo(false);
        assertThat(s2.getPreview().get("reingested")).isEqualTo(false);

        // No fresh CLASSIFICATION stubs invented by NLP
        long classif = s2.getRuleCandidates().stream()
                .filter(r -> r.getExpression() != null
                        && "CLASSIFICATION".equalsIgnoreCase(String.valueOf(r.getExpression().get("op"))))
                .count();
        assertThat(classif).isZero();
    }

    @Test
    void typedValues_booleanNumericPercentMoneyDurationEnumParamRef_retained() {
        UUID v1 = buildApprovedSimplePolicy();
        // Add enum + param-ref
        demoService.addPlainEnglishRule(v1, Map.of(
                "confirm", true, "mode", "BUILD",
                "parameterId", "application.borrower_type",
                "operator", "is", "value", "COMPANY", "treatment", "Reject"), null);
        // Force ACTIVE for createNewVersion after adding rules — re-approve lifecycle meta
        markApproved(orchestrator.requireSession(v1), "v1");

        Map<String, Object> created = demoService.createLifecycleVersion(v1, Map.of(), null);
        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));
        PolicyStudioSession s2 = orchestrator.requireSession(v2);

        assertThat(constOf(findByParam(s2, "kyc.pan.verified"))).isEqualTo(true);
        assertThat(asNumber(constOf(findByParam(s2, "bureau.score")))).isEqualByComparingTo("650");
        assertThat(asNumber(constOf(findByParam(s2, "obligation.ratio")))).isEqualByComparingTo("50");
        assertThat(asNumber(constOf(findByParam(s2, "application.requested_amount"))))
                .isEqualByComparingTo("1000000");
        assertThat(asNumber(constOf(findByParam(s2, "application.business_vintage_months"))))
                .isEqualByComparingTo("24");
        assertThat(String.valueOf(constOf(findByParam(s2, "application.borrower_type"))))
                .isEqualTo("COMPANY");
    }

    @Test
    void compoundRule_andPolicyAdjustments_andManualResolution_retained() {
        UUID v1Id = UUID.randomUUID();
        PolicyStudioSession v1 = newSession("Bureau BRE", v1Id);
        UUID clauseParent = UUID.randomUUID();
        UUID clauseChild = UUID.randomUUID();
        v1.getClauses().add(CiPolicyClause.builder()
                .id(clauseParent).policyDocumentId(v1Id)
                .sourceText("Overdue Exception Eligibility")
                .section("Bureau").sortOrder(0).build());
        v1.getClauses().add(CiPolicyClause.builder()
                .id(clauseChild).policyDocumentId(v1Id).parentClauseId(clauseParent)
                .sourceText("clean history >= 6 months")
                .section("Bureau").sortOrder(1).build());
        v1.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID()).clauseId(clauseParent)
                .systemRuleId("BUREAU_OVERDUE_EXCEPTION")
                .expression(Map.of("op", "AND_CHILDREN", "parent", "OVERDUE_EXCEPTION",
                        "children", List.of("1", "2", "3", "4")))
                .metadata(new LinkedHashMap<>(Map.of(
                        "disposition", "ACCEPTED",
                        "cmAuthored", true,
                        "compoundParent", true,
                        "businessTitle", "Overdue Exception Eligibility")))
                .build());
        v1.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID()).clauseId(clauseChild)
                .systemRuleId("BUREAU_CLEAN_HISTORY")
                .expression(Map.of("op", "GTE",
                        "left", Map.of("metric", "bureau.credit_after_overdue.clean_history_months"),
                        "right", Map.of("const", 6)))
                .metadata(new LinkedHashMap<>(Map.of(
                        "disposition", "ACCEPTED",
                        "cmAuthored", true,
                        "compoundChild", true,
                        "parameterResolutions", Map.of(
                                "clean_history", ParameterResolutionSupport.manual(
                                        "Clean history months", "Integer", "MONTHS",
                                        "Credit Analyst", "Manual capture", "CLEAN")))))
                .build());
        v1.getMetricCandidates().add(CiPolicyMetricCandidate.builder()
                .id(UUID.randomUUID()).clauseId(clauseParent)
                .metricName("Adjusted ADB")
                .baseMetric("banking.avg_daily_balance_3m")
                .exclusions(List.of("loan_credits", "gaming_credits"))
                .expression(Map.of("bulkDepositMultiple", 10))
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessMeasure", true,
                        "policyAdjustments", List.of(
                                "exclude loan credits", "exclude gaming credits", "bulk >10×"))))
                .build());
        // DELETED must not resurrect
        v1.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID()).clauseId(clauseParent)
                .systemRuleId("DELETED_RULE")
                .expression(Map.of("op", "EQ", "left", Map.of("metric", "x"), "right", Map.of("const", 1)))
                .metadata(new LinkedHashMap<>(Map.of("disposition", "DELETED")))
                .build());
        // IGNORED stays excluded-as-ignored
        v1.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID()).clauseId(clauseParent)
                .systemRuleId("IGNORED_RULE")
                .expression(Map.of("op", "EQ", "left", Map.of("metric", "y"), "right", Map.of("const", 1)))
                .metadata(new LinkedHashMap<>(Map.of("disposition", "IGNORED")))
                .build());
        markApproved(v1, "v1");
        orchestrator.persistence().saveSessionSnapshot(v1);

        Map<String, Object> out = lifecycle.createNewVersion(v1, Map.of());
        UUID v2Id = UUID.fromString(String.valueOf(out.get("documentId")));
        PolicyStudioSession v2 = orchestrator.requireSession(v2Id);

        assertThat(v2.getRuleCandidates().stream().map(CiPolicyRuleCandidate::getSystemRuleId))
                .contains("BUREAU_OVERDUE_EXCEPTION", "BUREAU_CLEAN_HISTORY", "IGNORED_RULE")
                .doesNotContain("DELETED_RULE");
        CiPolicyRuleCandidate parent = v2.getRuleCandidates().stream()
                .filter(r -> "BUREAU_OVERDUE_EXCEPTION".equals(r.getSystemRuleId())).findFirst().orElseThrow();
        assertThat(parent.getExpression().get("op")).isEqualTo("AND_CHILDREN");
        assertThat(parent.getExpression().get("children")).asList().hasSize(4);

        CiPolicyMetricCandidate adj = v2.getMetricCandidates().get(0);
        assertThat(adj.getExclusions()).contains("loan_credits", "gaming_credits");
        assertThat(adj.getExpression().get("bulkDepositMultiple")).isEqualTo(10);

        CiPolicyRuleCandidate clean = v2.getRuleCandidates().stream()
                .filter(r -> "BUREAU_CLEAN_HISTORY".equals(r.getSystemRuleId())).findFirst().orElseThrow();
        Map<String, Object> res = ParameterResolutionSupport.resolutionFor(
                clean.getMetadata(), "clean_history");
        assertThat(res.get("status")).isEqualTo(ParameterResolutionSupport.STATUS_MANUAL);
        assertThat(res.containsKey("testValue")).isFalse();
    }

    @Test
    void temporaryTestValue_notRetained_approvalsAndTestEvidenceReset() {
        UUID v1 = buildApprovedSimplePolicy();
        PolicyStudioSession s1 = orchestrator.requireSession(v1);
        CiPolicyRuleCandidate pan = findByParam(s1, "kyc.pan.verified");
        Map<String, Object> meta = new LinkedHashMap<>(pan.getMetadata());
        Map<String, Object> resolutions = new LinkedHashMap<>(
                ParameterResolutionSupport.resolutionsOf(meta));
        Map<String, Object> manual = ParameterResolutionSupport.manual(
                "Proposed EDI", "Money", "INR", "Analyst", null, "EDI");
        manual.put("testValue", 99999);
        manual.put("temporaryValue", 99999);
        resolutions.put("proposed_edi", manual);
        meta.put(ParameterResolutionSupport.META_KEY, resolutions);
        meta.put("testValues", Map.of("proposed_edi", 99999));
        pan.setMetadata(meta);
        s1.getReviews().add(CiPolicyReview.builder()
                .id(UUID.randomUUID()).tenantId(s1.getDocument().getTenantId())
                .policyDocumentId(v1).subjectType("DOCUMENT").subjectId(v1)
                .reviewState(ReviewState.CHECKER_APPROVED.name())
                .reviewer("policy_checker").createdAt(Instant.now()).build());
        s1.getTestCases().add(CiPolicyTestCase.builder()
                .id(UUID.randomUUID()).name("Boundary").expectedOutcome("PASS")
                .ruleCandidateId(pan.getId())
                .reviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name())
                .reviewedBy("cm").approvedAt(Instant.now())
                .metadata(new LinkedHashMap<>(Map.of("passed", true, "lastResult", "PASS")))
                .build());
        s1.setSimulation(new LinkedHashMap<>(Map.of("simulationReviewed", true, "runId", "sim-1")));
        markApproved(s1, "v1");
        orchestrator.persistence().saveSessionSnapshot(s1);

        Map<String, Object> created = demoService.createLifecycleVersion(v1, Map.of(), null);
        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));
        PolicyStudioSession s2 = orchestrator.requireSession(v2);

        assertThat(s2.getReviews()).isEmpty();
        assertThat(s2.getSimulation()).isEmpty();
        assertThat(s2.getTestCases()).isNotEmpty();
        assertThat(s2.getTestCases().get(0).getReviewStatus()).isEqualTo(ReviewState.AI_DRAFTED.name());
        assertThat(s2.getTestCases().get(0).getApprovedAt()).isNull();
        assertThat(s2.getTestCases().get(0).getMetadata().get("passed")).isNull();

        CiPolicyRuleCandidate pan2 = findByParam(s2, "kyc.pan.verified");
        assertThat(pan2.getMetadata().get("testValues")).isNull();
        Map<String, Object> res2 = ParameterResolutionSupport.resolutionFor(
                pan2.getMetadata(), "proposed_edi");
        assertThat(res2.get("status")).isEqualTo(ParameterResolutionSupport.STATUS_MANUAL);
        assertThat(res2.get("testValue")).isNull();
        assertThat(res2.get("temporaryValue")).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> life = (Map<String, Object>) created.get("lifecycle");
        assertThat(life.get("businessStatus")).isEqualTo(PolicyBusinessLifecycleStatus.DRAFT);
        assertThat(life.get("approvedBy")).isNull();
    }

    @Test
    void scopeRetained_oldVersionImmutable_v2EditDoesNotMutateV1() {
        UUID v1 = buildApprovedSimplePolicy();
        PolicyStudioSession s1 = orchestrator.requireSession(v1);
        Map<String, Object> life = new LinkedHashMap<>((Map<String, Object>)
                s1.getDocument().getMetadata().get(PolicyLifecycleService.META_KEY));
        Map<String, Object> app = new LinkedHashMap<>((Map<String, Object>) life.get("applicability"));
        app.put("borrowerType", "COMPANY");
        app.put("facilityType", "TERM_LOAN");
        app.put("maxLoanAmount", 1000000);
        life.put("applicability", app);
        s1.getDocument().getMetadata().put(PolicyLifecycleService.META_KEY, life);
        orchestrator.persistence().saveSessionSnapshot(s1);

        Map<String, Object> created = demoService.createLifecycleVersion(v1, Map.of(), null);
        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));

        PolicyStudioSession s2 = orchestrator.requireSession(v2);
        @SuppressWarnings("unchecked")
        Map<String, Object> life2 = (Map<String, Object>)
                s2.getDocument().getMetadata().get(PolicyLifecycleService.META_KEY);
        @SuppressWarnings("unchecked")
        Map<String, Object> app2 = (Map<String, Object>) life2.get("applicability");
        assertThat(app2.get("borrowerType")).isEqualTo("COMPANY");
        assertThat(app2.get("facilityType")).isEqualTo("TERM_LOAN");
        assertThat(life2.get("businessStatus")).isEqualTo(PolicyBusinessLifecycleStatus.DRAFT);

        // Edit v2 bureau score 650 → 675
        CiPolicyRuleCandidate bureau2 = findByParam(s2, "bureau.score");
        @SuppressWarnings("unchecked")
        Map<String, Object> right = (Map<String, Object>) bureau2.getExpression().get("right");
        right.put("const", 675);
        orchestrator.persistence().saveSessionSnapshot(s2);

        PolicyStudioSession s1Reload = orchestrator.requireSession(v1);
        assertThat(asNumber(constOf(findByParam(s1Reload, "bureau.score")))).isEqualByComparingTo("650");
        @SuppressWarnings("unchecked")
        Map<String, Object> life1 = (Map<String, Object>)
                s1Reload.getDocument().getMetadata().get(PolicyLifecycleService.META_KEY);
        assertThat(life1.get("businessStatus")).isIn(
                PolicyBusinessLifecycleStatus.APPROVED,
                PolicyBusinessLifecycleStatus.ACTIVE,
                PolicyBusinessLifecycleStatus.SCHEDULED);
        assertThat(asNumber(constOf(findByParam(orchestrator.requireSession(v2), "bureau.score"))))
                .isEqualByComparingTo("675");
    }

    @Test
    void copyPolicy_remainsSeparateSemanticPath() {
        UUID v1 = buildApprovedSimplePolicy();
        Map<String, Object> copy = demoService.copyPolicy(
                v1, Map.of("policyName", "SME Term Loan Policy (Copy)"), null);
        assertThat(copy.get("structuredClone")).isEqualTo(true);
        assertThat(copy.get("reingested")).isEqualTo(false);
        assertThat(String.valueOf(copy.get("copiedFromLabel"))).contains("Copied from:");
        UUID copyId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) copy.get("policyHeader")).get("documentId")));
        assertThat(copyId).isNotEqualTo(v1);

        Map<String, Object> versioned = demoService.createLifecycleVersion(v1, Map.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> lineage = (Map<String, Object>) versioned.get("lineage");
        assertThat(lineage.get("to")).isEqualTo("v2");
        assertThat(lineage.get("from")).isEqualTo("v1");
        // Copy is not v2 of same family
        assertThat(copyId.toString()).isNotEqualTo(String.valueOf(lineage.get("toDocumentId")));
    }

    @Test
    void bankingBre_structuredVersion_retainsUwRules() {
        Map<String, Object> banking = demoService.resetDemo("banking");
        UUID v1 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) banking.get("policyHeader")).get("documentId")));
        PolicyStudioSession s1 = orchestrator.requireSession(v1);
        long before = StructuredPolicySessionCloner.countUnderwritingRules(s1);
        assertThat(before).isGreaterThanOrEqualTo(1);
        markApproved(s1, "v1");
        orchestrator.persistence().saveSessionSnapshot(s1);

        Map<String, Object> created = demoService.createLifecycleVersion(v1, Map.of(), null);
        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));
        PolicyStudioSession s2 = orchestrator.requireSession(v2);
        assertThat(StructuredPolicySessionCloner.countUnderwritingRules(s2)).isEqualTo(before);
        assertThat(created.get("reingested")).isEqualTo(false);
        assertThat(s2.getMetricCandidates().size()).isEqualTo(s1.getMetricCandidates().size());
    }

    @Test
    void readinessConvergence_notRegressed_allowCanonicalFalse() {
        UUID v1 = buildApprovedSimplePolicy();
        // Add ADB >= EDI unresolved param-ref
        demoService.addPlainEnglishRule(v1, Map.of(
                "confirm", true, "mode", "BUILD",
                "parameterId", "banking.avg_daily_balance_3m",
                "operator", ">=",
                "valueMode", "PARAMETER",
                "rightParameterId", "application.proposed_edi",
                "treatment", "Reject"), null);
        markApproved(orchestrator.requireSession(v1), "v1");
        Map<String, Object> created = demoService.createLifecycleVersion(v1, Map.of(), null);
        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));
        PolicyStudioSession s2 = orchestrator.requireSession(v2);
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.sessionExecutionBlockers(s2);
        assertThat(blockers.toString().toLowerCase()).containsAnyOf("edi", "unresolved", "proposed");
        Map<String, Object> stats = PolicyExecutionReadiness.executionReadinessStats(s2);
        assertThat(stats.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(properties.getCutover().isAllowCanonicalAuthority()).isFalse();
    }

    // ── helpers ────────────────────────────────────────────────────

    private UUID buildApprovedSimplePolicy() {
        Map<String, Object> scratch = demoService.createFromScratch(
                Map.of("policyName", "SME Term Loan Policy"), "credit_manager", null);
        UUID docId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) scratch.get("policyHeader")).get("documentId")));
        addRule(docId, "kyc.pan.verified", "is", true);
        addRule(docId, "bureau.score", ">=", 650);
        addRule(docId, "obligation.ratio", "<=", 50);
        addRule(docId, "application.business_vintage_months", ">=", 24);
        addRule(docId, "application.requested_amount", "<=", 1_000_000);
        markApproved(orchestrator.requireSession(docId), "v1");
        orchestrator.persistence().saveSessionSnapshot(orchestrator.requireSession(docId));
        return docId;
    }

    private void addRule(UUID docId, String parameterId, String op, Object value) {
        demoService.addPlainEnglishRule(docId, Map.of(
                "confirm", true,
                "mode", "BUILD",
                "parameterId", parameterId,
                "operator", op,
                "value", value,
                "treatment", "Reject"), null);
    }

    private void markApproved(PolicyStudioSession session, String version) {
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("policyVersion", version);
        life.put("policyType", "CREDIT_POLICY");
        life.put("businessStatus", PolicyBusinessLifecycleStatus.ACTIVE);
        life.put("approvedBy", "credit_manager");
        life.put("checker", "policy_checker");
        life.put("createdBy", "credit_manager");
        life.put("contentImmutable", true);
        life.put("lineageId", session.documentId().toString());
        life.put("applicability", new LinkedHashMap<>(Map.of(
                "products", List.of("BUSINESS_TERM_LOAN"),
                "borrowerType", "COMPANY",
                "effectiveFrom", "2026-01-01")));
        Map<String, Object> meta = session.getDocument().getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(session.getDocument().getMetadata());
        meta.put(PolicyLifecycleService.META_KEY, life);
        session.getDocument().setMetadata(meta);
        session.getDocument().setStatus(DocumentStatus.DRAFT_READY.name());
    }

    private PolicyStudioSession newSession(String name, UUID id) {
        PolicyStudioSession s = new PolicyStudioSession();
        s.setDocument(CiPolicyDocument.builder()
                .id(id)
                .tenantId(properties.getDefaultTenantId())
                .name(name)
                .documentType("TXT")
                .contentHash(UUID.randomUUID().toString())
                .status(DocumentStatus.DRAFT_READY.name())
                .documentVersion(1)
                .sourceText(name + " structured sample")
                .productScope("DIGILEAP")
                .uploadedBy("credit_manager")
                .metadata(new LinkedHashMap<>())
                .build());
        return s;
    }

    private static CiPolicyRuleCandidate findByParam(PolicyStudioSession s, String parameterId) {
        return s.getRuleCandidates().stream()
                .filter(r -> {
                    Map<String, Object> m = r.getMetadata() == null ? Map.of() : r.getMetadata();
                    if (parameterId.equals(String.valueOf(m.get("parameterId")))) return true;
                    Object left = r.getExpression() == null ? null : r.getExpression().get("left");
                    if (left instanceof Map<?, ?> l) {
                        return parameterId.equals(String.valueOf(l.get("metric")));
                    }
                    return false;
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing rule for " + parameterId));
    }

    private static Object constOf(CiPolicyRuleCandidate r) {
        Object right = r.getExpression().get("right");
        if (right instanceof Map<?, ?> m) {
            return m.get("const");
        }
        return right;
    }

    private static BigDecimal asNumber(Object v) {
        return new BigDecimal(String.valueOf(v));
    }
}
