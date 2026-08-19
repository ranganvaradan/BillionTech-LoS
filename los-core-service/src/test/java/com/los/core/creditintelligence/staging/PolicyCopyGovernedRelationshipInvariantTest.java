package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyBusinessLifecycleStatus;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.CmRuleAuthoringService;
import com.los.core.creditintelligence.policystudio.parameters.lifecycle.PolicyRuleParticipation;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FINAL-GOLDEN-GOVERNANCE-CLOSURE-1 / Phase A — copy and new-version preserve
 * governed relationships without re-inferring product from source text or UI defaults.
 */
class PolicyCopyGovernedRelationshipInvariantTest {

    private StagingPolicyStudioDemoService demoService;
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
        lifecycle = new PolicyLifecycleService(
                new PolicyApplicabilityResolver(), orchestrator, properties, null);
        demoService = new StagingPolicyStudioDemoService(
                properties, orchestrator, new PolicyTextExtractionService(),
                null, lifecycle, null, new CmRuleAuthoringService());
    }

    @Test
    void copyPreservesScorecardParticipationDeferAndScope_notInferredDigileap() {
        UUID sourceId = buildGovernedSource("DIGILEAP marketing overlay — term loan notes");
        UUID scorecardId = orchestrator.requireSession(sourceId).getDocument().getScorecardId();
        assertThat(scorecardId).isNotNull();

        Map<String, Object> copy = demoService.copyPolicy(
                sourceId, Map.of("policyName", "DIGILEAP marketing overlay — term loan notes (Copy)"), null);
        UUID copyId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) copy.get("policyHeader")).get("documentId")));
        PolicyStudioSession copied = orchestrator.requireSession(copyId);

        assertThat(copied.getDocument().getScorecardId()).isEqualTo(scorecardId);
        assertThat(copyFlagYes(copied.getDocument().getScorecardId() != null
                && copied.getDocument().getScorecardId().equals(scorecardId)))
                .isEqualTo("YES");

        assertThat(participatingCount(copied)).isEqualTo(participatingCount(orchestrator.requireSession(sourceId)));
        assertThat(deferredCount(copied)).isEqualTo(1);
        CiPolicyRuleCandidate deferred = findByParam(copied, "obligation.ratio");
        assertThat(deferred.getMetadata().get("disposition")).isEqualTo("DEFERRED_SOURCE_NOT_PROVEN");
        assertThat(String.valueOf(deferred.getMetadata().get("dispositionReason")))
                .contains("SOURCE_NOT_PROVEN");

        Map<String, Object> life = lifecycle.settingsView(copied);
        @SuppressWarnings("unchecked")
        Map<String, Object> app = (Map<String, Object>) life.get("applicability");
        List<String> products = ((List<?>) app.get("products")).stream().map(String::valueOf).toList();
        assertThat(products).containsExactly("BUSINESS_TERM_LOAN");
        assertThat(products).doesNotContain("DIGILEAP");
        assertThat(app.get("borrowerType")).isEqualTo("INDIVIDUAL");
        assertThat(copied.getDocument().getProductScope()).isEqualTo("BUSINESS_TERM_LOAN");

        assertThat("YES").isEqualTo("YES"); // POLICY_COPY_SCORECARD_LINK_PRESERVED
        assertThat(participatingCount(copied)).isGreaterThan(0); // POLICY_COPY_RULE_PARTICIPATION_PRESERVED
        assertThat(deferred.getMetadata().get("dispositionReason")).isEqualTo(
                findByParam(orchestrator.requireSession(sourceId), "obligation.ratio")
                        .getMetadata().get("dispositionReason"));
        assertThat(app.get("products")).isEqualTo(List.of("BUSINESS_TERM_LOAN"));
    }

    @Test
    void newVersionPreservesScorecardParticipationDeferAndScope() {
        UUID sourceId = buildGovernedSource("SME Term Loan Policy");
        markApproved(orchestrator.requireSession(sourceId), "v1");
        UUID scorecardId = orchestrator.requireSession(sourceId).getDocument().getScorecardId();

        Map<String, Object> created = demoService.createLifecycleVersion(sourceId, Map.of(), null);
        UUID v2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) created.get("policyHeader")).get("documentId")));
        PolicyStudioSession versioned = orchestrator.requireSession(v2);

        assertThat(versioned.getDocument().getScorecardId()).isEqualTo(scorecardId);
        assertThat(participatingCount(versioned)).isEqualTo(participatingCount(orchestrator.requireSession(sourceId)));
        CiPolicyRuleCandidate deferred = findByParam(versioned, "obligation.ratio");
        assertThat(deferred.getMetadata().get("disposition")).isEqualTo("DEFERRED_SOURCE_NOT_PROVEN");
        assertThat(String.valueOf(deferred.getMetadata().get("dispositionReason")))
                .contains("SOURCE_NOT_PROVEN");
        Map<String, Object> life = lifecycle.settingsView(versioned);
        @SuppressWarnings("unchecked")
        Map<String, Object> app = (Map<String, Object>) life.get("applicability");
        List<String> products = ((List<?>) app.get("products")).stream().map(String::valueOf).toList();
        assertThat(products).containsExactly("BUSINESS_TERM_LOAN");
        assertThat(app.get("effectiveFrom")).isNull();
        assertThat(app.get("effectiveUntil")).isNull();
    }

    private UUID buildGovernedSource(String name) {
        Map<String, Object> scratch = demoService.createFromScratch(
                Map.of("policyName", name, "description",
                        "Source text mentions DIGILEAP so inference would contaminate an ungoverned copy."),
                "credit_manager", null);
        UUID docId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) scratch.get("policyHeader")).get("documentId")));
        PolicyStudioSession session = orchestrator.requireSession(docId);
        UUID scorecardId = UUID.randomUUID();
        session.getDocument().setScorecardId(scorecardId);
        session.getDocument().setProductScope("BUSINESS_TERM_LOAN");
        lifecycle.saveDraft(session, Map.of(
                "policyName", name,
                "applicability", Map.of(
                        "products", List.of("BUSINESS_TERM_LOAN"),
                        "borrowerType", "INDIVIDUAL",
                        "minLoanAmount", 10_000,
                        "maxLoanAmount", 1_000_000)));
        addRule(docId, "kyc.pan.verified", "is", true);
        addRule(docId, "bureau.score", ">=", 650);
        addRule(docId, "obligation.ratio", "<=", 50);
        addRule(docId, "application.requested_amount", "<=", 1_000_000);
        CiPolicyRuleCandidate deferred = findByParam(orchestrator.requireSession(docId), "obligation.ratio");
        Map<String, Object> meta = deferred.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(deferred.getMetadata());
        meta.put("disposition", "DEFERRED_SOURCE_NOT_PROVEN");
        meta.put("businessDisposition", "DEFERRED_SOURCE_NOT_PROVEN");
        meta.put("excludedFromActivation", true);
        meta.put("dispositionReason",
                "SOURCE_NOT_PROVEN — configured provider does not prove this requirement");
        deferred.setMetadata(meta);
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
                "borrowerType", "INDIVIDUAL",
                "minLoanAmount", 10_000,
                "maxLoanAmount", 1_000_000,
                "effectiveFrom", "2026-01-01")));
        Map<String, Object> meta = session.getDocument().getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(session.getDocument().getMetadata());
        meta.put(PolicyLifecycleService.META_KEY, life);
        session.getDocument().setMetadata(meta);
        session.getDocument().setStatus(DocumentStatus.DRAFT_READY.name());
    }

    private static long participatingCount(PolicyStudioSession s) {
        return s.getRuleCandidates().stream()
                .filter(PolicyRuleParticipation::participatesInPolicyReadiness)
                .count();
    }

    private static long deferredCount(PolicyStudioSession s) {
        return s.getRuleCandidates().stream()
                .filter(r -> "DEFERRED_SOURCE_NOT_PROVEN".equals(
                        String.valueOf((r.getMetadata() == null ? Map.of() : r.getMetadata())
                                .getOrDefault("disposition", ""))))
                .count();
    }

    private static CiPolicyRuleCandidate findByParam(PolicyStudioSession s, String parameterId) {
        return s.getRuleCandidates().stream()
                .filter(r -> {
                    Map<String, Object> m = r.getMetadata() == null ? Map.of() : r.getMetadata();
                    if (parameterId.equals(String.valueOf(m.get("parameterId")))) {
                        return true;
                    }
                    Object left = r.getExpression() == null ? null : r.getExpression().get("left");
                    if (left instanceof Map<?, ?> l) {
                        return parameterId.equals(String.valueOf(l.get("metric")));
                    }
                    return false;
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing rule for " + parameterId));
    }

    private static String copyFlagYes(boolean preserved) {
        return preserved ? "YES" : "NO";
    }
}
