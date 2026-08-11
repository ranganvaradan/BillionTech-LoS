package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyApplicabilityResolver;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.ParameterResolutionSupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthoringCompleteness;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-READINESS-CONVERGENCE-1 — Rules ↔ Versions share one execution readiness model.
 */
class PolicyReadinessConvergenceTest {

    @Test
    void unresolvedRequiredOperand_ruleNotReady() {
        CiPolicyRuleCandidate r = adbEdiRule(null);
        assertThat(PolicyAuthoringCompleteness.isAuthoringComplete(r)).isTrue();
        assertThat(PolicyExecutionReadiness.isExecutionReady(r)).isFalse();
        assertThat(PolicyExecutionReadiness.unresolvedOperands(r))
                .anyMatch(op -> "proposed_edi".equals(op.get("operandKey")));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("status", "Ready");
        PolicyAuthoringCompleteness.reconcileCard(card, r);
        assertThat(card.get("status")).isEqualTo("Needs your input");
        assertThat(card.get("executionReady")).isEqualTo(false);
    }

    @Test
    void sameBlockerAppearsInLifecycle() {
        PolicyStudioSession session = sessionWith(adbEdiRule(null));
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.sessionExecutionBlockers(session);
        assertThat(blockers).isNotEmpty();
        Set<String> keys = blockers.stream()
                .map(b -> String.valueOf(b.get("blockerKey")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(keys).anyMatch(k -> k.contains("UNRESOLVED_OPERAND") && k.contains("proposed_edi")
                || k.contains("Proposed EDI") || k.toLowerCase().contains("edi"));

        PolicyLifecycleService life = lifecycle();
        Map<String, Object> settings = life.settingsView(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exec = (List<Map<String, Object>>) settings.get("executionBlockers");
        assertThat(exec).isNotNull();
        Set<String> lifeKeys = exec.stream()
                .map(b -> String.valueOf(b.get("blockerKey")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(lifeKeys).isEqualTo(keys);
    }

    @Test
    void acceptedPlusUnresolved_isNotReady() {
        CiPolicyRuleCandidate r = adbEdiRule(null);
        r.getMetadata().put("disposition", "ACCEPTED");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("status", "Accepted");
        PolicyAuthoringCompleteness.reconcileCard(card, r);
        assertThat(card.get("reviewDisposition")).isEqualTo("ACCEPTED");
        assertThat(card.get("status")).isEqualTo("Needs your input");
        assertThat(card.get("executionReady")).isEqualTo(false);
    }

    @Test
    void configuredManualOperand_resolvesReadiness() {
        Map<String, Object> manual = ParameterResolutionSupport.manual(
                "Proposed EDI", "Money", "INR", "Credit Analyst", "Enter proposed EDI", "Proposed EDI");
        CiPolicyRuleCandidate r = adbEdiRule(Map.of("proposed_edi", manual));
        assertThat(PolicyExecutionReadiness.isExecutionReady(r)).isTrue();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("status", "Needs your input");
        PolicyAuthoringCompleteness.reconcileCard(card, r);
        assertThat(card.get("executionReady")).isEqualTo(true);
        assertThat(card.get("status")).isIn("Ready", "Accepted", "Edited");
    }

    @Test
    void incompleteManual_isNeedsConfigurationNotReady() {
        Map<String, Object> bare = new LinkedHashMap<>();
        bare.put("status", ParameterResolutionSupport.STATUS_MANUAL);
        bare.put("resolutionType", ParameterResolutionSupport.TYPE_MANUAL);
        // missing enteredBy / dataType / capture path
        CiPolicyRuleCandidate r = adbEdiRule(Map.of("proposed_edi", bare));
        assertThat(PolicyExecutionReadiness.isExecutionReady(r)).isFalse();
        assertThat(PolicyExecutionReadiness.executionBlockersForRule(r))
                .anyMatch(b -> "NEEDS_CONFIGURATION".equals(b.get("blockerType")));
    }

    @Test
    void testOnlyValueDoesNotResolveProductionReadiness() {
        CiPolicyRuleCandidate r = adbEdiRule(null);
        // Simulate test-only override metadata that must not heal production resolution
        r.getMetadata().put("testValues", Map.of("proposed_edi", 50000));
        r.getMetadata().put("lastTestOverrides", Map.of("application.proposed_edi", 50000));
        assertThat(PolicyExecutionReadiness.isExecutionReady(r)).isFalse();
        assertThat(PolicyExecutionReadiness.sessionExecutionBlockers(sessionWith(r)))
                .anyMatch(b -> String.valueOf(b.get("reason")).toLowerCase().contains("edi"));
    }

    @Test
    void reportOnlyUnresolved_doesNotBlockActivation() {
        PolicyStudioSession session = sessionWith(adbEdiRule(Map.of(
                "proposed_edi",
                ParameterResolutionSupport.manual("Proposed EDI", "Money", "INR",
                        "Credit Analyst", null, "EDI"))));
        // Open ambiguity for large credits — report-only
        session.getAmbiguities().add(CiPolicyAmbiguity.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .ambiguityType("TERM")
                .phrase("Large credit definition")
                .description("Define large credit for reporting")
                .severity("MATERIAL")
                .resolutionStatus("OPEN")
                .build());
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.sessionExecutionBlockers(session);
        assertThat(blockers).noneMatch(b -> String.valueOf(b.get("reason")).toLowerCase().contains("large"));
        assertThat(PolicyExecutionReadiness.classifyDataCalcRole(
                "banking.large_credit_transactions", "REPORT_ANALYST_INFORMATION",
                "Large credits shown separately", session))
                .isEqualTo(PolicyExecutionReadiness.ROLE_REPORT_OR_ANALYST);
        assertThat(PolicyExecutionReadiness.roleBlocksActivation(
                PolicyExecutionReadiness.ROLE_REPORT_OR_ANALYST)).isFalse();
    }

    @Test
    void unusedDataRequirement_doesNotBlock() {
        assertThat(PolicyExecutionReadiness.roleBlocksActivation(
                PolicyExecutionReadiness.ROLE_UNUSED)).isFalse();
        assertThat(PolicyExecutionReadiness.roleBlocksActivation(
                PolicyExecutionReadiness.ROLE_OPTIONAL)).isFalse();
    }

    @Test
    void requiredCalculationDependency_blocks() {
        assertThat(PolicyExecutionReadiness.roleBlocksActivation(
                PolicyExecutionReadiness.ROLE_REQUIRED_DEPENDENCY)).isTrue();
        assertThat(PolicyExecutionReadiness.roleBlocksActivation(
                PolicyExecutionReadiness.ROLE_REQUIRED_OPERAND)).isTrue();
    }

    @Test
    void requiredPolicyAdjustmentNotImplemented_blocks() {
        CiPolicyRuleCandidate adb = adbEdiRule(Map.of(
                "proposed_edi",
                ParameterResolutionSupport.manual("Proposed EDI", "Money", "INR",
                        "Credit Analyst", null, "EDI")));
        CiPolicyRuleCandidate adj = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("METRIC_ADJ_ADB_BULK")
                .expression(Map.of("op", "EXISTS"))
                .metadata(new LinkedHashMap<>(Map.of(
                        "metricAdjustment", true,
                        "classificationOnly", true,
                        "affectedMetric", "banking.avg_daily_balance_3m",
                        "businessSummary", "Exclude bulk deposits greater than 10 times average deposits")))
                .build();
        PolicyStudioSession session = sessionWith(adb, adj);
        assertThat(PolicyExecutionReadiness.sessionExecutionBlockers(session))
                .anyMatch(b -> PolicyExecutionReadiness.BLOCKER_REQUIRED_ADJUSTMENT
                        .equals(b.get("blockerType")));
    }

    @Test
    void ignoredRule_doesNotBlock() {
        CiPolicyRuleCandidate r = adbEdiRule(null);
        r.getMetadata().put("disposition", "IGNORED");
        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(r)).isFalse();
        assertThat(PolicyExecutionReadiness.sessionExecutionBlockers(sessionWith(r))).isEmpty();
    }

    @Test
    void deletedRule_doesNotBlock() {
        CiPolicyRuleCandidate r = adbEdiRule(null);
        r.getMetadata().put("deleted", true);
        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(r)).isFalse();
        assertThat(PolicyExecutionReadiness.sessionExecutionBlockers(sessionWith(r))).isEmpty();
    }

    @Test
    void excludedRule_doesNotBlock() {
        CiPolicyRuleCandidate r = adbEdiRule(null);
        r.getMetadata().put("excludedFromActivation", true);
        assertThat(PolicyExecutionReadiness.isIncludedExecutableRule(r)).isFalse();
        assertThat(PolicyExecutionReadiness.sessionExecutionBlockers(sessionWith(r))).isEmpty();
    }

    @Test
    void genuineBoundaryAmbiguity_blocksExactRule() {
        CiPolicyRuleCandidate inward = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("BANK_INWARD_RETURN_COUNT")
                .expression(Map.of("op", "GT", "left", Map.of("metric", "banking.inward_return_count"),
                        "right", Map.of("const", 100)))
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessTitle", "Inward Return Rule",
                        "catalogueBacked", true,
                        "parameters", Map.of("maximumCount", 100))))
                .build();
        PolicyStudioSession session = sessionWith(inward);
        session.getAmbiguities().add(CiPolicyAmbiguity.builder()
                .id(UUID.randomUUID())
                .clauseId(inward.getClauseId())
                .ambiguityType("BOUNDARY_AMBIGUITY")
                .phrase("exactly 100 transactions")
                .description("Boundary at 100 undefined")
                .severity("MATERIAL")
                .resolutionStatus("OPEN")
                .build());
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.sessionExecutionBlockers(session);
        assertThat(blockers).anyMatch(b ->
                PolicyExecutionReadiness.BLOCKER_BOUNDARY_AMBIGUITY.equals(b.get("blockerType"))
                        && String.valueOf(b.get("reason")).contains("100"));
        assertThat(blockers).anyMatch(b -> "Inward Return Rule".equals(b.get("ruleName")));
    }

    @Test
    void staleReportAmbiguity_doesNotBlock() {
        PolicyStudioSession session = sessionWith(simpleBureauRule());
        session.getAmbiguities().add(CiPolicyAmbiguity.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .ambiguityType("TERM")
                .phrase("intercompany merchant group")
                .description("Analyst definition")
                .severity("MATERIAL")
                .resolutionStatus("OPEN")
                .build());
        assertThat(PolicyExecutionReadiness.sessionExecutionBlockers(session)).isEmpty();
    }

    @Test
    void rulesAndVersions_executionBlockerSetsIdentical() {
        PolicyStudioSession session = sessionWith(adbEdiRule(null));
        List<Map<String, Object>> fromHelper = PolicyExecutionReadiness.sessionExecutionBlockers(session);
        Map<String, Object> view = new LinkedHashMap<>();
        ProspectDay2ViewBuilder.enrich(view, session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fromRules = (List<Map<String, Object>>) view.get("executionBlockers");
        PolicyLifecycleService life = lifecycle();
        Map<String, Object> settings = life.settingsView(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fromVersions = (List<Map<String, Object>>) settings.get("executionBlockers");
        Set<String> a = keysOf(fromHelper);
        Set<String> b = keysOf(fromRules);
        Set<String> c = keysOf(fromVersions);
        assertThat(b).isEqualTo(a);
        assertThat(c).isEqualTo(a);
    }

    @Test
    void governanceBlockersRemainSeparate() {
        PolicyStudioSession session = sessionWith(simpleBureauRule());
        List<Map<String, Object>> exec = PolicyExecutionReadiness.sessionExecutionBlockers(session);
        List<Map<String, Object>> gov = PolicyExecutionReadiness.sessionGovernanceBlockers(
                false, false, false, false);
        assertThat(exec).noneMatch(b -> "GOVERNANCE".equals(b.get("category")));
        assertThat(gov).allMatch(b -> "GOVERNANCE".equals(b.get("category")));
        assertThat(gov).extracting(b -> b.get("blockerType"))
                .contains("SCOPE", "POLICY_TEST", "CM_APPROVAL", "CHECKER_APPROVAL");
    }

    @Test
    void dataReadinessDenominator_usesRequiredExecutableInputsOnly() {
        PolicyStudioSession session = sessionWith(adbEdiRule(null));
        Map<String, Object> stats = PolicyExecutionReadiness.executionReadinessStats(session);
        assertThat(((Number) stats.get("requiredInputCount")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) stats.get("dataReadinessPercent")).intValue()).isLessThan(100);
        // After MANUAL resolve, percent rises
        CiPolicyRuleCandidate resolved = adbEdiRule(Map.of(
                "proposed_edi",
                ParameterResolutionSupport.manual("Proposed EDI", "Money", "INR",
                        "Credit Analyst", null, "EDI")));
        Map<String, Object> after = PolicyExecutionReadiness.executionReadinessStats(sessionWith(resolved));
        assertThat(((Number) after.get("dataReadinessPercent")).intValue())
                .isGreaterThan(((Number) stats.get("dataReadinessPercent")).intValue());
    }

    @Test
    void bureauClean_unresolvedBlocksCompoundParent() {
        CiPolicyRuleCandidate parent = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("BUREAU_OVERDUE_EXCEPTION_PARENT")
                .expression(Map.of("op", "AND"))
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessTitle", "No overdue except clean history",
                        "catalogueBacked", true,
                        "parameters", Map.of("windowMonths", 24))))
                .build();
        assertThat(PolicyExecutionReadiness.isExecutionReady(parent)).isFalse();
        assertThat(PolicyExecutionReadiness.unresolvedOperands(parent))
                .anyMatch(op -> "clean_history".equals(op.get("operandKey")));
        Map<String, Object> manual = ParameterResolutionSupport.manual(
                "Clean months", "Integer", "months", "Credit Analyst", null, "CLEAN");
        parent.getMetadata().put(ParameterResolutionSupport.META_KEY, Map.of("clean_history", manual));
        assertThat(PolicyExecutionReadiness.isExecutionReady(parent)).isTrue();
    }

    @Test
    void simpleBureauThreshold650_remainsAuthoringComplete() {
        CiPolicyRuleCandidate r = simpleBureauRule();
        assertThat(PolicyAuthoringCompleteness.isAuthoringComplete(r)).isTrue();
        assertThat(PolicyExecutionReadiness.isExecutionReady(r)).isTrue();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("status", "Needs your input");
        card.put("blockedReason", PolicyAuthoringCompleteness.LEGACY_VALUE_MISSING);
        PolicyAuthoringCompleteness.reconcileCard(card, r);
        assertThat(card.get("status")).isIn("Ready", "Accepted", "Edited");
        assertThat(card.get("authoringComplete")).isEqualTo(true);
        assertThat(card.get("runtimeValueRequired")).isEqualTo(false);
    }

    @Test
    void allowCanonicalAuthorityRemainsFalse() {
        PolicyStudioSession session = sessionWith(simpleBureauRule());
        Map<String, Object> stats = PolicyExecutionReadiness.executionReadinessStats(session);
        assertThat(stats.get("allowCanonicalAuthority")).isEqualTo(false);
        PolicyLifecycleService life = lifecycle();
        Map<String, Object> settings = life.settingsView(session);
        assertThat(settings.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    private static PolicyLifecycleService lifecycle() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCutover().setAllowCanonicalAuthority(false);
        return new PolicyLifecycleService(new PolicyApplicabilityResolver(), null, props, null);
    }

    private static Set<String> keysOf(List<Map<String, Object>> blockers) {
        if (blockers == null) return Set.of();
        return blockers.stream()
                .map(b -> String.valueOf(b.get("blockerKey")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static PolicyStudioSession sessionWith(CiPolicyRuleCandidate... rules) {
        PolicyStudioSession s = new PolicyStudioSession();
        UUID tenantId = UUID.randomUUID();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Readiness fixture")
                .documentType("TXT")
                .contentHash("hash")
                .status(DocumentStatus.DRAFT_READY.name())
                .documentVersion(1)
                .sourceText("fixture")
                .productScope("DIGILEAP")
                .uploadedBy("test")
                .metadata(new LinkedHashMap<>())
                .build();
        s.setDocument(doc);
        s.getRuleCandidates().addAll(List.of(rules));
        return s;
    }

    private static CiPolicyRuleCandidate adbEdiRule(Map<String, Object> resolutions) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("businessTitle", "DigiLeap — Banking Capacity");
        meta.put("catalogueBacked", true);
        meta.put("parameters", Map.of("ratio", 1));
        meta.put("threshold", 1);
        if (resolutions != null) {
            meta.put(ParameterResolutionSupport.META_KEY, new LinkedHashMap<>(resolutions));
        }
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("BANK_ADB_GE_PROPOSED_EDI")
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", "banking.avg_daily_balance_3m"),
                        "right", Map.of("metric", "application.proposed_edi")))
                .onTrue("PASS")
                .onFalse("FAIL")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(meta)
                .build();
    }

    private static CiPolicyRuleCandidate simpleBureauRule() {
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CATALOGUE_BUREAU_MIN_SCORE")
                .expression(Map.of(
                        "op", "LT",
                        "left", Map.of("metric", "bureau.score"),
                        "right", Map.of("const", 650)))
                .onTrue("FAIL")
                .onFalse("PASS")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessCapabilityId", "BUREAU.MIN_SCORE",
                        "businessTitle", "Minimum Bureau Score",
                        "catalogueBacked", true,
                        "parameters", Map.of("minimumScore", 650L),
                        "threshold", 650L,
                        "failureTreatment", "REJECT")))
                .build();
    }
}
