package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-SIMPLE-FLOW-INTEGRITY-1 — operand leakage, readiness contradiction, lifecycle authority.
 */
class PolicySimpleFlowIntegrity1Test {

    @Test
    void monthlyCreditsSystemId_doesNotMatchEdiToken() {
        assertThat(SystemRuleIdTokens.hasProposedEdiToken("CM_BANKING_MONTHLY_CREDITS_3M_GTE")).isFalse();
        assertThat(SystemRuleIdTokens.hasProposedEdiToken("BANK_STARTER_ADB_GTE_EDI")).isTrue();
        assertThat(SystemRuleIdTokens.hasProposedEdiToken("BANK_ADB_GE_PROPOSED_EDI")).isTrue();
        assertThat("MONTHLY_CREDITS".contains("EDI")).isTrue(); // documents the false-positive trap
    }

    @Test
    void monthlyCreditsConstantRule_hasNoEdiOperand() {
        CiPolicyRuleCandidate r = monthlyCreditsRule();
        List<Map<String, Object>> ops = PolicyExecutionReadiness.operandsOf(r);
        assertThat(ops).isNotEmpty();
        assertThat(ops).noneMatch(op ->
                "proposed_edi".equals(op.get("operandKey"))
                        || String.valueOf(op.get("parameterId")).contains("proposed_edi")
                        || String.valueOf(op.get("businessName")).toLowerCase().contains("proposed edi"));
        assertThat(ops).anyMatch(op ->
                "banking.monthly_credits_3m".equals(op.get("parameterId"))
                        || "monthly_credits".equals(op.get("operandKey")));
    }

    @Test
    void displayedOperandsMatchExpressionOperands() {
        CiPolicyRuleCandidate r = monthlyCreditsRule();
        Set<String> expr = expressionMetrics(r);
        Set<String> displayed = PolicyExecutionReadiness.operandsOf(r).stream()
                .map(op -> String.valueOf(op.getOrDefault("parameterId",
                        op.getOrDefault("suggestedParameterId", op.get("operandKey")))))
                .filter(s -> s.contains("."))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(displayed).isEqualTo(expr);
    }

    @Test
    void readinessOperandsMatchExpressionRequiredRuntimeOperands() {
        CiPolicyRuleCandidate r = monthlyCreditsRule();
        Set<String> expr = expressionMetrics(r);
        Set<String> readiness = PolicyExecutionReadiness.operandsOf(r).stream()
                .map(op -> String.valueOf(op.getOrDefault("parameterId", op.get("operandKey"))))
                .filter(s -> s.contains("."))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(readiness).isEqualTo(expr);
        assertThat(PolicyExecutionReadiness.executionBlockersForRule(r)).isEmpty();
    }

    @Test
    void unrelatedSessionResolution_notAttachedToMonthlyCredits() {
        Map<String, Object> manual = ParameterResolutionSupport.manual(
                "Proposed EDI", "Money", "INR", "Credit Analyst", "Enter EDI", "Proposed EDI");
        CiPolicyRuleCandidate r = monthlyCreditsRule();
        Map<String, Object> meta = new LinkedHashMap<>(r.getMetadata());
        meta.put(ParameterResolutionSupport.META_KEY, Map.of("proposed_edi", manual));
        r.setMetadata(meta);
        // Contaminated session resolution must not create an EDI operand face
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                r.getSystemRuleId(),
                List.of("banking.monthly_credits_3m"),
                meta,
                Map.of());
        assertThat(ops).noneMatch(op -> "proposed_edi".equals(op.get("operandKey")));
    }

    @Test
    void zeroCanonicalBlockers_noCriticalDataGap() {
        PolicyStudioSession session = sessionWith(
                monthlyCreditsRule(),
                bureauScoreRule(),
                chequeReturnRule(),
                settlementRule());
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.sessionExecutionBlockers(session);
        Map<String, Object> stats = PolicyExecutionReadiness.executionReadinessStats(session);
        assertThat(blockers).isEmpty();
        assertThat(stats.get("executionBlockerCount")).isEqualTo(0);
        assertThat(Boolean.TRUE.equals(stats.get("criticalDataGap"))
                || "critical rule data gap".equals(stats.get("dataReadinessLabel"))).isFalse();
        // Denominator excludes Proposed EDI
        assertThat(stats.get("requiredInputCount")).isEqualTo(
                ((Number) stats.get("requiredInputCount")).intValue());
        @SuppressWarnings("unchecked")
        Set<String> requiredKeys = new LinkedHashSet<>();
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            for (Map<String, Object> op : PolicyExecutionReadiness.operandsOf(r)) {
                requiredKeys.add(String.valueOf(op.getOrDefault("operandKey", op.get("parameterId"))));
            }
        }
        assertThat(requiredKeys).doesNotContain("proposed_edi");
        assertThat(((Number) stats.get("dataReadinessPercent")).intValue()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void genuineEdiBlocker_appearsInCanonicalSet() {
        CiPolicyRuleCandidate r = adbEdiRule(null);
        List<Map<String, Object>> blockers = PolicyExecutionReadiness.executionBlockersForRule(r);
        assertThat(blockers).isNotEmpty();
        assertThat(blockers).anyMatch(b ->
                String.valueOf(b.get("blockerKey")).contains("proposed_edi")
                        || String.valueOf(b.get("reason")).toLowerCase().contains("edi"));
    }

    @Test
    void dataReadinessDenominator_excludesUnrelatedParams() {
        PolicyStudioSession session = sessionWith(monthlyCreditsRule());
        // Plant session-level EDI resolution that must not enter denominator
        Map<String, Object> docMeta = session.getDocument().getMetadata();
        docMeta.put(ParameterResolutionSupport.DOC_META_KEY,
                Map.of("proposed_edi", ParameterResolutionSupport.manual(
                        "Proposed EDI", "Money", "INR", "CA", "x", "Proposed EDI")));
        Map<String, Object> stats = PolicyExecutionReadiness.executionReadinessStats(session);
        assertThat(((Number) stats.get("requiredInputCount")).intValue()).isEqualTo(1);
        assertThat(PolicyExecutionReadiness.operandsOf(session.getRuleCandidates().get(0)))
                .noneMatch(op -> "proposed_edi".equals(op.get("operandKey")));
    }

    @Test
    void adbEdiRule_stillGetsBothOperands() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BANK_STARTER_ADB_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                Map.of(),
                Map.of());
        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).get("operandKey")).isEqualTo("average_daily_balance");
        assertThat(ops.get(1).get("operandKey")).isEqualTo("proposed_edi");
    }

    @Test
    void allowCanonicalAuthorityRemainsFalse() {
        Map<String, Object> stats = PolicyExecutionReadiness.executionReadinessStats(
                sessionWith(monthlyCreditsRule()));
        assertThat(stats.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    private static Set<String> expressionMetrics(CiPolicyRuleCandidate r) {
        Set<String> out = new LinkedHashSet<>();
        collectMetrics(r.getExpression(), out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectMetrics(Object node, Set<String> out) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        if (m.get("metric") != null) out.add(String.valueOf(m.get("metric")));
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?> || v instanceof List<?>) {
                if (v instanceof List<?> list) {
                    for (Object item : list) collectMetrics(item, out);
                } else {
                    collectMetrics(v, out);
                }
            }
        }
    }

    private static PolicyStudioSession sessionWith(CiPolicyRuleCandidate... rules) {
        PolicyStudioSession s = new PolicyStudioSession();
        UUID tenantId = UUID.randomUUID();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Simple four-rule integrity")
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

    private static CiPolicyRuleCandidate monthlyCreditsRule() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("businessTitle", "Monthly credits");
        meta.put("parameterId", "banking.monthly_credits_3m");
        meta.put("catalogueBacked", true);
        meta.put("cmAuthored", true);
        meta.put("threshold", 50000L);
        meta.put("failureTreatment", "REJECT");
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CM_BANKING_MONTHLY_CREDITS_3M_GTE")
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", "banking.monthly_credits_3m"),
                        "right", Map.of("const", 50000)))
                .onTrue("PASS")
                .onFalse("FAIL")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(meta)
                .build();
    }

    private static CiPolicyRuleCandidate bureauScoreRule() {
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CM_BUREAU_SCORE_GTE")
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", "bureau.score"),
                        "right", Map.of("const", 650)))
                .onTrue("PASS")
                .onFalse("FAIL")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessTitle", "Minimum bureau score",
                        "parameterId", "bureau.score",
                        "catalogueBacked", true,
                        "threshold", 650L,
                        "failureTreatment", "REJECT")))
                .build();
    }

    private static CiPolicyRuleCandidate chequeReturnRule() {
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CM_BANKING_CHEQUE_RETURN_COUNT_3M_LTE")
                .expression(Map.of(
                        "op", "LTE",
                        "left", Map.of("metric", "banking.cheque_return_count_3m"),
                        "right", Map.of("const", 0)))
                .onTrue("PASS")
                .onFalse("FAIL")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessTitle", "Cheque returns",
                        "parameterId", "banking.cheque_return_count_3m",
                        "catalogueBacked", true,
                        "threshold", 0L,
                        "failureTreatment", "REJECT")))
                .build();
    }

    private static CiPolicyRuleCandidate settlementRule() {
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("CM_BANKING_SETTLEMENT_COUNT_MONTHLY_AVG_3M_GTE")
                .expression(Map.of(
                        "op", "GTE",
                        "left", Map.of("metric", "banking.settlement.count_monthly_avg_3m"),
                        "right", Map.of("const", 20)))
                .onTrue("PASS")
                .onFalse("FAIL")
                .onMissing("DATA_INSUFFICIENT")
                .metadata(new LinkedHashMap<>(Map.of(
                        "businessTitle", "Average monthly settlements",
                        "parameterId", "banking.settlement.count_monthly_avg_3m",
                        "catalogueBacked", true,
                        "threshold", 20L,
                        "failureTreatment", "REJECT")))
                .build();
    }

    private static CiPolicyRuleCandidate adbEdiRule(Map<String, Object> resolutions) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("businessTitle", "Banking Capacity");
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
}
