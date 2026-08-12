package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.metrics.AdbBulkDepositAdjustmentCalculator;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyReviewService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioDurableResolutionStore;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioPersistenceService;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import com.los.core.creditintelligence.staging.StagingPolicyStudioDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-RESOLUTION-PERSISTENCE-P0 — resolved → persisted → reconstructed → inherited
 * for parameter, adjustment, and boundary resolutions. Selective invalidation on ADB wording change.
 */
class PolicyResolutionPersistenceP0Test {

    @TempDir
    Path durableDir;

    private StagingPolicyStudioDemoService demo;
    private PolicyStudioOrchestrator orch;
    private PolicyStudioPersistenceService persistence;
    private PolicyLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CreditIntelligenceProperties properties = new CreditIntelligenceProperties();
        properties.setDefaultTenantId(tenantId);
        properties.getStagingDemo().setEnabled(true);
        properties.getCutover().setAllowCanonicalAuthority(false);

        PolicyStudioDurableResolutionStore durable =
                new PolicyStudioDurableResolutionStore(durableDir.toString());
        persistence = new PolicyStudioPersistenceService(durable);
        PolicyReviewService review = new PolicyReviewService(
                properties, null, persistence, null);
        orch = new PolicyStudioOrchestrator(
                properties, null, null, null, null, null, null, null, null, null, null, null, null, null,
                review, persistence, null, null, null, null, null, null, null);
        lifecycle = new PolicyLifecycleService(
                new PolicyApplicabilityResolver(), orch, properties, null);
        demo = new StagingPolicyStudioDemoService(
                properties, orch, new PolicyTextExtractionService(),
                null, lifecycle, null, new CmRuleAuthoringService());
    }

    @Test
    void parameter_adjustment_boundary_surviveRestartResumeClone_andSelectiveInvalidate() {
        // Fresh Banking BRE (intentional wipe of durable latest index)
        Map<String, Object> opened = demo.resetDemo("banking");
        UUID docId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) opened.get("policyHeader")).get("documentId")));
        PolicyStudioSession s = orch.requireSession(docId);

        resolveEdi(docId, s);
        resolveInward100(docId, s);
        resolveAdbBulk(docId, s);

        assertThreeResolved(orch.requireSession(docId));

        // SAVE already happened on each resolve — simulate reload via clearCache
        persistence.clearCache();
        assertThreeResolved(orch.requireSession(docId));

        // Simulate process restart (in-memory wipe; durable overlays remain)
        Map<String, Object> bundleBefore = PolicyResolutionIdentity.extractBundle(orch.requireSession(docId));
        assertThat(bundleBefore.get("policyDataResolutions")).isInstanceOf(Map.class);
        persistence.simulateProcessRestart();
        assertThat(persistence.loadSession(docId)).isNull();

        // Re-open Banking demo — must rebind durable resolutions (not leave Needs Configuration)
        Map<String, Object> resumed = demo.build("banking");
        UUID reboundId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) resumed.get("policyHeader")).get("documentId")));
        PolicyStudioSession afterRestart = orch.requireSession(reboundId);
        assertThreeResolved(afterRestart);

        // Submit / approve path (staging stamp) then clone next version
        demo.stampActiveForVersioning(reboundId, Map.of("reason", "persistence-golden"), null);
        Map<String, Object> cloned = demo.createLifecycleVersion(
                reboundId, Map.of("reasonForChange", "Next version inherits resolutions"), null);
        UUID v2Id = UUID.fromString(String.valueOf(
                ((Map<?, ?>) cloned.get("policyHeader")).get("documentId")));
        PolicyStudioSession v2 = orch.requireSession(v2Id);
        assertThreeResolved(v2);
        // V1 immutable — prior rebound id still holds resolutions
        assertThreeResolved(orch.requireSession(reboundId));

        // Change only ADB bulk wording on V2 → only ADB stales
        CiPolicyRuleCandidate adbRule = v2.getRuleCandidates().stream()
                .filter(r -> {
                    String n = r.getMetadata() == null ? ""
                            : String.valueOf(r.getMetadata().getOrDefault("businessTitle", "")).toLowerCase();
                    String src = r.getLineage() == null ? ""
                            : String.valueOf(r.getLineage().getOrDefault("sourceText", "")).toLowerCase();
                    return n.contains("bulk") || src.contains("10 times") || src.contains("bulk deposition");
                })
                .findFirst()
                .orElse(v2.getRuleCandidates().get(0));
        String priorText = adbRule.getLineage() != null && adbRule.getLineage().get("sourceText") != null
                ? String.valueOf(adbRule.getLineage().get("sourceText"))
                : "Any bulk deposition by merchant which is more than 10 times of average deposits";
        String newText = priorText.replace("10 times", "15 times").replace("10×", "15×");
        if (newText.equals(priorText)) {
            newText = priorText + " (threshold revised to 15×)";
        }
        assertThat(PolicyResolutionIdentity.adbWordingChanged(priorText, newText)).isTrue();

        Map<String, Object> lineage = adbRule.getLineage() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(adbRule.getLineage());
        lineage.put("priorSourceText", priorText);
        lineage.put("sourceText", newText);
        adbRule.setLineage(lineage);
        PolicyResolutionIdentity.invalidateAdbBulk(v2);
        persistence.saveSessionSnapshot(v2);

        PolicyStudioSession afterEdit = orch.requireSession(v2Id);
        assertThat(hasAdbReady(afterEdit)).isFalse();
        assertThat(hasEdiMapped(afterEdit)).isTrue();
        assertThat(hasInwardBoundary(afterEdit)).isTrue();
    }

    @Test
    void identity_preventsDuplicateUnresolvedAfterRebind() {
        Map<String, Object> opened = demo.resetDemo("banking");
        UUID docId = UUID.fromString(String.valueOf(
                ((Map<?, ?>) opened.get("policyHeader")).get("documentId")));
        resolveInward100(docId, orch.requireSession(docId));
        persistence.simulateProcessRestart();
        Map<String, Object> again = demo.build("banking");
        UUID id2 = UUID.fromString(String.valueOf(
                ((Map<?, ?>) again.get("policyHeader")).get("documentId")));
        PolicyStudioSession s = orch.requireSession(id2);
        long openHundred = s.getAmbiguities().stream()
                .filter(a -> a.getPhrase() != null && a.getPhrase().toLowerCase().contains("100"))
                .filter(a -> "OPEN".equalsIgnoreCase(a.getResolutionStatus()))
                .count();
        assertThat(openHundred).isZero();
        assertThat(hasInwardBoundary(s)).isTrue();
    }

    private void resolveEdi(UUID docId, PolicyStudioSession s) {
        var amb = s.getAmbiguities().stream()
                .filter(a -> a.getPhrase() != null && "EDI".equalsIgnoreCase(a.getPhrase().trim()))
                .findFirst()
                .orElseThrow();
        demo.resolveAmbiguity(docId, amb.getId(), Map.of(
                "action", AmbiguityResolutionAction.SELECT_CANDIDATE.name(),
                "resolvedOption", "application.proposed_edi",
                "resolvedBy", "credit_manager",
                "notes", "Map Proposed EDI"), null);

        CiPolicyRuleCandidate ediRule = orch.requireSession(docId).getRuleCandidates().stream()
                .filter(r -> String.valueOf(r.getExpression()).toLowerCase().contains("proposed_edi")
                        || SystemRuleIdTokens.hasProposedEdiToken(r.getSystemRuleId()))
                .findFirst()
                .orElseThrow();
        demo.reviewRule(docId, ediRule.getId(), Map.of(
                "uiAction", "RESOLVE_PARAMETER_MAP",
                "operandKey", "proposed_edi",
                "originalTerm", "Proposed EDI",
                "parameterId", "application.proposed_edi",
                "reviewer", "credit_manager"), null);
    }

    private void resolveInward100(UUID docId, PolicyStudioSession s) {
        var amb = s.getAmbiguities().stream()
                .filter(a -> a.getPhrase() != null && a.getPhrase().toLowerCase().contains("100"))
                .findFirst()
                .orElseThrow();
        demo.resolveAmbiguity(docId, amb.getId(), Map.of(
                "action", AmbiguityResolutionAction.SELECT_CANDIDATE.name(),
                "resolvedOption", InwardReturnCompoundSupport.OPTION_RATIO,
                "resolvedBy", "credit_manager",
                "notes", "Exactly 100 → ratio branch"), null);
    }

    private void resolveAdbBulk(UUID docId, PolicyStudioSession s) {
        CiPolicyRuleCandidate rule = s.getRuleCandidates().stream()
                .filter(r -> {
                    String n = r.getMetadata() == null ? ""
                            : String.valueOf(r.getMetadata().getOrDefault("businessTitle", "")).toLowerCase();
                    String src = r.getLineage() == null ? ""
                            : String.valueOf(r.getLineage().getOrDefault("sourceText", "")).toLowerCase();
                    return n.contains("bulk") || src.contains("bulk") || src.contains("10 times");
                })
                .findFirst()
                .orElse(s.getRuleCandidates().get(0));
        demo.reviewRule(docId, rule.getId(), Map.of(
                "uiAction", "RESOLVE_DATA_ADJUSTMENT",
                "dataItemId", AdbBulkDepositAdjustmentCalculator.ADJUSTMENT_ID,
                "multiple", new BigDecimal("10"),
                "periodMonths", 3,
                "confirmExecutable", true,
                "excludeLoanDisbursements", true,
                "excludeOnlineGaming", true,
                "excludeDuplicates", true,
                "reviewer", "credit_manager"), null);
    }

    private static void assertThreeResolved(PolicyStudioSession s) {
        assertThat(hasEdiMapped(s)).as("EDI parameter resolution").isTrue();
        assertThat(hasInwardBoundary(s)).as("Inward =100 boundary").isTrue();
        assertThat(hasAdbReady(s)).as("ADB bulk adjustment READY").isTrue();
    }

    @SuppressWarnings("unchecked")
    private static boolean hasAdbReady(PolicyStudioSession s) {
        Map<String, Object> all = PolicyDataResolutionSupport.fromDocument(s);
        Object row = all.get(AdbBulkDepositAdjustmentCalculator.ADJUSTMENT_ID);
        if (!(row instanceof Map<?, ?> m)) return false;
        if (!"READY".equals(String.valueOf(m.get("executionStatus")))
                && !"READY".equals(String.valueOf(m.get("cmStatus")))) {
            return false;
        }
        Object defObj = m.get("definition");
        String binding = null;
        if (defObj instanceof Map<?, ?> d) {
            binding = d.get("binding") == null ? null : String.valueOf(d.get("binding"));
        }
        if (binding == null && m.get("binding") != null) {
            binding = String.valueOf(m.get("binding"));
        }
        return AdbBulkDepositAdjustmentCalculator.BINDING.equals(binding);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasEdiMapped(PolicyStudioSession s) {
        if (s.getDocument() != null && s.getDocument().getMetadata() != null) {
            Object ppm = s.getDocument().getMetadata().get(ParameterResolutionSupport.DOC_META_KEY);
            if (ppm instanceof Map<?, ?> maps) {
                Object row = maps.get("proposed_edi");
                if (row instanceof Map<?, ?> m && ParameterResolutionSupport.isResolved(
                        (Map<String, Object>) m)) {
                    return true;
                }
            }
        }
        return s.getRuleCandidates().stream().anyMatch(r -> {
            Map<String, Object> res = ParameterResolutionSupport.resolutionFor(
                    r.getMetadata(), "proposed_edi");
            return ParameterResolutionSupport.isResolved(res);
        });
    }

    private static boolean hasInwardBoundary(PolicyStudioSession s) {
        boolean ambOk = s.getAmbiguities().stream()
                .filter(a -> a.getPhrase() != null && a.getPhrase().toLowerCase().contains("100"))
                .anyMatch(a -> "RESOLVED".equalsIgnoreCase(a.getResolutionStatus())
                        || "SUPERSEDED".equalsIgnoreCase(a.getResolutionStatus()));
        boolean ruleOk = s.getRuleCandidates().stream()
                .anyMatch(r -> Boolean.TRUE.equals(
                        r.getMetadata() == null ? null : r.getMetadata().get("boundaryResolved")));
        return ambOk && ruleOk;
    }
}
