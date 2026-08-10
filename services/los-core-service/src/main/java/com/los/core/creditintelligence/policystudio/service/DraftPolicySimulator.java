package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.core.clock.EvaluationClock;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicySimulationRun;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simulates draft rules via PolicyDslInterpreterV1 against fixture metric/fact maps.
 * Labelled VALIDATION_FIXTURE_SIMULATION — not portfolio impact.
 */
@Service
public class DraftPolicySimulator {

    public static final String LABEL = "VALIDATION_FIXTURE_SIMULATION";

    private static final List<String> CASES = List.of(
            "CASE_A", "CASE_B", "CASE_C", "CASE_D", "CASE_E");

    private final PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();
    private final PolicyStudioPersistenceService persistenceService;

    public DraftPolicySimulator(PolicyStudioPersistenceService persistenceService) {
        this.persistenceService = persistenceService != null ? persistenceService : new PolicyStudioPersistenceService();
    }

    public DraftPolicySimulator() {
        this(new PolicyStudioPersistenceService());
    }

    public Map<String, Object> simulate(PolicyStudioSession session) {
        return simulate(session, FixedEvaluationClock.atLocalNoon(
                java.time.LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata")));
    }

    public Map<String, Object> simulate(PolicyStudioSession session, EvaluationClock clock) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int pass = 0, fail = 0, refer = 0, di = 0;

        Map<String, Map<String, Object>> caseMetrics = stubCaseMetrics();
        Map<String, Object> policyParams = new LinkedHashMap<>();
        session.getParameters().forEach(p -> policyParams.put(p.getCode(), null));

        for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
            for (String caseCode : CASES) {
                Map<String, Object> metrics = caseMetrics.get(caseCode);
                String outcome = evaluateWithDsl(rule, metrics, Map.of(), policyParams, clock);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rule", rule.getSystemRuleId());
                row.put("case", caseCode);
                row.put("inputValues", metrics);
                row.put("outcome", outcome);
                row.put("expectedTestOutcome", null);
                row.put("actualOutcome", outcome);
                row.put("dslVersion", PolicyDslInterpreterV1.DSL_VERSION);
                rows.add(row);
                switch (outcome) {
                    case "PASS" -> pass++;
                    case "FAIL" -> fail++;
                    case "REFER" -> refer++;
                    default -> di++;
                }
            }
        }

        for (CiPolicyTestCase tc : session.getTestCases()) {
            CiPolicyRuleCandidate rule = session.ruleById(tc.getRuleCandidateId());
            if (rule == null) {
                continue;
            }
            String actual = evaluateWithDsl(rule, tc.getInputMetrics(), tc.getInputFacts(), policyParams, clock);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rule", rule.getSystemRuleId());
            row.put("case", "BRE_FIXTURE:" + tc.getName());
            row.put("inputValues", Map.of("facts", tc.getInputFacts(), "metrics", tc.getInputMetrics()));
            row.put("outcome", actual);
            row.put("expectedTestOutcome", tc.getExpectedOutcome());
            row.put("actualOutcome", actual);
            row.put("match", tc.getExpectedOutcome() != null && tc.getExpectedOutcome().equals(actual));
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("label", LABEL);
        summary.put("disclaimer", "Not portfolio impact — validation fixtures only");
        summary.put("dslVersion", PolicyDslInterpreterV1.DSL_VERSION);
        summary.put("evaluationSemantics", PolicyDslInterpreterV1.EVALUATION_SEMANTICS);
        summary.put("casesSimulated", CASES.size());
        summary.put("pass", pass);
        summary.put("fail", fail);
        summary.put("refer", refer);
        summary.put("dataInsufficient", di);
        summary.put("rows", rows);
        session.setSimulation(summary);

        CiPolicySimulationRun run = CiPolicySimulationRun.builder()
                .id(UUID.randomUUID())
                .tenantId(session.getDocument().getTenantId())
                .sessionId(session.sessionId())
                .draftPackageId(session.getDraftPackage() == null ? null : session.getDraftPackage().getId())
                .simulationLabel(LABEL)
                .dslVersion(PolicyDslInterpreterV1.DSL_VERSION)
                .evaluationSemantics(PolicyDslInterpreterV1.EVALUATION_SEMANTICS)
                .summary(Map.of(
                        "pass", pass, "fail", fail, "refer", refer, "dataInsufficient", di,
                        "casesSimulated", CASES.size()))
                .caseResults(new ArrayList<>(rows))
                .build();
        persistenceService.saveSimulationRun(session, run);
        return summary;
    }

    private String evaluateWithDsl(
            CiPolicyRuleCandidate rule,
            Map<String, Object> metrics,
            Map<String, Object> facts,
            Map<String, Object> policyParams,
            EvaluationClock clock) {
        Map<String, Object> expr = rule.getExpression();
        if (expr == null || expr.isEmpty()) {
            return rule.getOnMissing() == null ? "DATA_INSUFFICIENT" : rule.getOnMissing();
        }
        // Enrich facts with common aliases
        Map<String, Object> factMap = new LinkedHashMap<>(facts == null ? Map.of() : facts);
        Map<String, Object> params = new LinkedHashMap<>(policyParams);
        if (factMap.containsKey("application.proposed_edi")) {
            params.putIfAbsent("PROPOSED_EDI", factMap.get("application.proposed_edi"));
        }
        var ctx = new PolicyDslInterpreterV1.EvaluationContext(
                metrics == null ? Map.of() : metrics,
                factMap,
                params,
                factMap,
                clock,
                rule.getOnMissing() == null ? "DATA_INSUFFICIENT" : rule.getOnMissing());
        String boolOutcome = interpreter.evaluate(expr, ctx);
        if ("DATA_INSUFFICIENT".equals(boolOutcome) || "REFER".equals(boolOutcome)) {
            return boolOutcome;
        }
        if ("PASS".equals(boolOutcome)) {
            // expression evaluated true → apply onTrue
            return rule.getOnTrue() == null ? "PASS" : rule.getOnTrue();
        }
        if ("FAIL".equals(boolOutcome)) {
            // expression evaluated false → apply onFalse
            return rule.getOnFalse() == null ? "FAIL" : rule.getOnFalse();
        }
        return boolOutcome;
    }

    private Map<String, Map<String, Object>> stubCaseMetrics() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        m.put("CASE_A", Map.of(
                "banking.avg_daily_balance_3m", 150000,
                "bureau.score", 720,
                "bureau.max_dpd_6m", 0,
                "bureau.inquiries.current_month", 1,
                "bureau.inquiries.current_month_count", 1));
        m.put("CASE_B", Map.of(
                "banking.avg_daily_balance_3m", 120000,
                "bureau.score", 680,
                "bureau.max_dpd_6m", 0));
        m.put("CASE_C", Map.of(
                "banking.avg_daily_balance_3m", 80000,
                "bureau.score", 700,
                "bureau.max_dpd_6m", 15));
        m.put("CASE_D", Map.of(
                "banking.avg_daily_balance_3m", 90000,
                "bureau.score", 710,
                "bureau.max_dpd_6m", 5,
                "bureau.inquiries.current_month", 2,
                "bureau.inquiries.current_month_count", 2));
        m.put("CASE_E", Map.of());
        return m;
    }
}
