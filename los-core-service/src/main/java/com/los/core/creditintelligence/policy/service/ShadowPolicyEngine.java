package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.domain.CiStandardRuleResult;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.CiPolicyStageResult;
import com.los.core.creditintelligence.policy.domain.CiScoreResult;
import com.los.core.creditintelligence.policy.domain.ExecutablePackageStatus;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.domain.PolicyOutcome;
import com.los.core.creditintelligence.policy.domain.PolicyRuleType;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * P1 Shadow Policy Engine — evaluates executable packages in SHADOW mode only.
 * NEVER sets status ACTIVE or affects production underwriting.
 */
@Service
public class ShadowPolicyEngine {

    public static final String ENGINE_NAME = "POLICY_DSL_V1";
    public static final String ENGINE_VERSION = "P1_SHADOW";

    private final PolicyDslInterpreterV1 interpreter;
    private final PolicyInputResolver inputResolver;
    private final DeclarativeOrchestrator orchestrator;
    private final ScorecardDefinitionExecutor scorecardExecutor;
    private final ContentHasher hasher;

    public ShadowPolicyEngine() {
        this(new PolicyDslInterpreterV1(), new PolicyInputResolver(),
                new DeclarativeOrchestrator(), new ScorecardDefinitionExecutor(), new ContentHasher());
    }

    public ShadowPolicyEngine(
            PolicyDslInterpreterV1 interpreter,
            PolicyInputResolver inputResolver,
            DeclarativeOrchestrator orchestrator,
            ScorecardDefinitionExecutor scorecardExecutor,
            ContentHasher hasher) {
        this.interpreter = interpreter != null ? interpreter : new PolicyDslInterpreterV1();
        this.inputResolver = inputResolver != null ? inputResolver : new PolicyInputResolver();
        this.orchestrator = orchestrator != null ? orchestrator : new DeclarativeOrchestrator();
        this.scorecardExecutor = scorecardExecutor != null ? scorecardExecutor : new ScorecardDefinitionExecutor();
        this.hasher = hasher != null ? hasher : new ContentHasher();
    }

    public CiPolicyEvaluation evaluate(CiExecutablePolicyPackage pkg, PolicyEvaluationInput input) {
        if (pkg == null || input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "package and input required");
        }
        if (pkg.getTenantId() == null || input.tenantId() == null
                || !pkg.getTenantId().equals(input.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant policy evaluation rejected");
        }
        if (ExecutablePackageStatus.ACTIVE.name().equals(pkg.getStatus())) {
            // P1 never treats ACTIVE as production authority — still evaluate as shadow only
            // but refuse to claim ACTIVE authority
        }
        Instant started = Instant.now();
        UUID evaluationId = UUID.randomUUID();

        Map<String, Object> content = pkg.getContent() == null ? Map.of() : pkg.getContent();
        Map<String, Map<String, Object>> rulesById = indexRules(content);
        List<DeclarativeOrchestrator.StageDefinition> stages = orchestrator.resolveStages(content);

        PolicyDslInterpreterV1.EvaluationContext dslCtx = inputResolver.toDslContext(
                input, Map.of(), PolicyDslInterpreterV1.DATA_INSUFFICIENT);

        List<CiPolicyStageResult> stageResults = new ArrayList<>();
        List<CiStandardRuleResult> ruleResults = new ArrayList<>();
        List<String> outcomeSeq = new ArrayList<>();
        int pass = 0, fail = 0, refer = 0, di = 0;
        boolean stopped = false;

        for (DeclarativeOrchestrator.StageDefinition stage : stages) {
            if (stopped) {
                break;
            }
            List<Map<String, Object>> stageRuleDetails = new ArrayList<>();
            List<String> stageOutcomes = new ArrayList<>();

            for (String ruleId : stage.rules()) {
                Map<String, Object> rule = rulesById.get(ruleId);
                if (rule == null) {
                    continue;
                }
                String outcome = evaluateRule(rule, input, dslCtx);
                PolicyRuleType ruleType = parseRuleType(rule.get("ruleType"));
                CiStandardRuleResult rr = CiStandardRuleResult.builder()
                        .id(UUID.randomUUID())
                        .evaluationId(evaluationId)
                        .ruleId(ruleId)
                        .ruleVersion(String.valueOf(rule.getOrDefault("version", "1")))
                        .policyVersionId(pkg.getId() == null ? evaluationId : pkg.getId())
                        .category(stage.stageCode())
                        .ruleType(ruleType.name())
                        .engineName(ENGINE_NAME)
                        .engineVersion(ENGINE_VERSION)
                        .outcome(outcome)
                        .severity(String.valueOf(rule.getOrDefault("severity", ruleType.name())))
                        .reasonCode(rule.get("reasonCode") == null ? null : String.valueOf(rule.get("reasonCode")))
                        .explanation(rule.get("explanation") == null ? null : String.valueOf(rule.get("explanation")))
                        .executedAt(Instant.now())
                        .trace(Map.of(
                                "stageCode", stage.stageCode(),
                                "dslVersion", PolicyDslInterpreterV1.DSL_VERSION,
                                "shadowOnly", true,
                                "productionActive", false))
                        .build();
                ruleResults.add(rr);
                stageOutcomes.add(outcome);
                outcomeSeq.add(ruleType.name() + ":" + outcome);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("ruleId", ruleId);
                detail.put("outcome", outcome);
                detail.put("ruleType", ruleType.name());
                stageRuleDetails.add(detail);
                switch (outcome) {
                    case PolicyDslInterpreterV1.PASS -> pass++;
                    case PolicyDslInterpreterV1.FAIL -> fail++;
                    case PolicyDslInterpreterV1.REFER -> refer++;
                    default -> di++;
                }
            }

            String stageOutcome = aggregateStage(stageOutcomes);
            boolean cont = orchestrator.shouldContinue(stage, stageOutcome);
            CiPolicyStageResult sr = CiPolicyStageResult.builder()
                    .id(UUID.randomUUID())
                    .evaluationId(evaluationId)
                    .stageCode(stage.stageCode())
                    .sequence(stage.sequence())
                    .outcome(stageOutcome)
                    .continueFlag(cont)
                    .ruleCount(stageRuleDetails.size())
                    .detail(Map.of("rules", stageRuleDetails))
                    .build();
            stageResults.add(sr);
            if (!cont) {
                stopped = true;
            }
        }

        Map<String, Object> scoreResultMap = Map.of();
        if (content.get("scorecard") instanceof Map<?, ?> sc) {
            @SuppressWarnings("unchecked")
            CiScoreResult score = scorecardExecutor.execute((Map<String, Object>) sc, input);
            scoreResultMap = scoreToMap(score);
            if (PolicyOutcome.DATA_INSUFFICIENT.name().equals(score.getGrade())) {
                outcomeSeq.add("SCORECARD:DATA_INSUFFICIENT");
                di++;
            } else if (PolicyOutcome.REFER.name().equals(score.getGrade())) {
                outcomeSeq.add("SCORECARD:REFER");
                refer++;
            }
        }

        String overall = aggregateOverall(content, ruleResults);
        String contentHash = pkg.getContentHash() != null ? pkg.getContentHash() : hasher.hashMap(content);
        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("contentHash", contentHash);
        hashPayload.put("evaluationContextId", input.evaluationContextId());
        hashPayload.put("outcomes", outcomeSeq);
        hashPayload.put("overall", overall);
        String detHash = hasher.hashMap(hashPayload);

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("engine", ENGINE_NAME);
        explanation.put("shadowOnly", true);
        explanation.put("productionActive", false);
        explanation.put("packageStatus", pkg.getStatus());
        explanation.put("overallOutcome", overall);
        explanation.put("stageCount", stageResults.size());
        explanation.put("precedence", precedenceList(content));

        return CiPolicyEvaluation.builder()
                .id(evaluationId)
                .evaluationContextId(input.evaluationContextId())
                .policyPackageId(pkg.getId())
                .policyVersion(pkg.getVersion())
                .status("COMPLETED")
                .overallOutcome(overall)
                .startedAt(started)
                .completedAt(Instant.now())
                .deterministicHash(detHash)
                .stageCount(stageResults.size())
                .ruleCount(ruleResults.size())
                .passCount(pass)
                .failCount(fail)
                .referCount(refer)
                .diCount(di)
                .evidenceRefs(List.of())
                .explanation(explanation)
                .scoreResult(scoreResultMap)
                .comparisonSummary(Map.of())
                .ruleResults(ruleResults)
                .stageResults(stageResults)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String evaluateRule(Map<String, Object> rule, PolicyEvaluationInput input,
                                PolicyDslInterpreterV1.EvaluationContext baseCtx) {
        PolicyRuleType ruleType = parseRuleType(rule.get("ruleType"));
        boolean allowsDefaulted = Boolean.TRUE.equals(rule.get("allowsDefaulted"));
        String onMissing = rule.get("onMissing") == null
                ? PolicyDslInterpreterV1.DATA_INSUFFICIENT
                : String.valueOf(rule.get("onMissing"));

        // DEFAULTED → for hard rules treat as DATA_INSUFFICIENT unless allowsDefaulted=true
        if (isHardLike(ruleType) && !allowsDefaulted && hasDefaultedHardInput(rule, input, ruleType)) {
            return PolicyDslInterpreterV1.DATA_INSUFFICIENT;
        }

        Map<String, Object> expr = rule.get("expression") instanceof Map<?, ?> em
                ? (Map<String, Object>) em : Map.of();
        if (expr.isEmpty()) {
            return onMissing;
        }

        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(
                baseCtx.metrics(), baseCtx.facts(), baseCtx.policyParameters(),
                baseCtx.applicationFields(), baseCtx.clock(), onMissing, baseCtx.reconciliations());

        String boolOutcome = interpreter.evaluate(expr, ctx);
        if (PolicyDslInterpreterV1.DATA_INSUFFICIENT.equals(boolOutcome)
                || PolicyDslInterpreterV1.REFER.equals(boolOutcome)
                || PolicyDslInterpreterV1.NOT_APPLICABLE.equals(boolOutcome)
                || PolicyDslInterpreterV1.ERROR.equals(boolOutcome)) {
            return boolOutcome;
        }
        // Map boolean PASS/FAIL through onTrue/onFalse (studio style: true condition often means FAIL for hard)
        if (PolicyDslInterpreterV1.PASS.equals(boolOutcome)) {
            return rule.get("onTrue") == null ? PolicyDslInterpreterV1.PASS : String.valueOf(rule.get("onTrue"));
        }
        if (PolicyDslInterpreterV1.FAIL.equals(boolOutcome)) {
            return rule.get("onFalse") == null ? PolicyDslInterpreterV1.FAIL : String.valueOf(rule.get("onFalse"));
        }
        return boolOutcome;
    }

    @SuppressWarnings("unchecked")
    private boolean hasDefaultedHardInput(Map<String, Object> rule, PolicyEvaluationInput input,
                                         PolicyRuleType ruleType) {
        Object refs = rule.get("inputRefs");
        if (!(refs instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (Object r : list) {
            if (!(r instanceof Map<?, ?> rm)) {
                continue;
            }
            String kind = rm.get("kind") == null ? "METRIC" : String.valueOf(rm.get("kind"));
            String reference = String.valueOf(rm.get("reference"));
            Object raw = peekRaw(kind, reference, input);
            if (raw instanceof Map<?, ?> mm
                    && "DEFAULTED".equalsIgnoreCase(String.valueOf(mm.get("dataStatus")))) {
                var resolved = inputResolver.resolve(kind, reference, input, ruleType, false);
                if (resolved.dataStatus() == com.los.core.creditintelligence.policy.domain.DataStatus.DATA_INSUFFICIENT) {
                    return true;
                }
            }
        }
        return false;
    }

    private Object peekRaw(String kind, String reference, PolicyEvaluationInput input) {
        String k = kind.toUpperCase(Locale.ROOT);
        return switch (k) {
            case "FACT", "FACT_REF" -> input.facts().get(reference);
            case "METRIC", "METRIC_REF" -> input.metrics().get(reference);
            case "RECONCILIATION", "RECON_REF" -> input.reconciliations().get(reference);
            case "POLICY_PARAMETER", "POLICY_PARAMETER_REF" -> input.policyParameters().get(reference);
            case "APPLICATION_FIELD", "APPLICATION_FIELD_REF" -> input.applicationFields().get(reference);
            default -> null;
        };
    }

    private boolean isHardLike(PolicyRuleType t) {
        return t == PolicyRuleType.HARD || t == PolicyRuleType.KNOCKOUT || t == PolicyRuleType.DATA_QUALITY;
    }

    private String aggregateStage(List<String> outcomes) {
        if (outcomes.isEmpty()) {
            return PolicyOutcome.NOT_APPLICABLE.name();
        }
        if (outcomes.contains(PolicyDslInterpreterV1.FAIL)) {
            return PolicyDslInterpreterV1.FAIL;
        }
        if (outcomes.contains(PolicyDslInterpreterV1.ERROR)) {
            return PolicyDslInterpreterV1.ERROR;
        }
        if (outcomes.contains(PolicyDslInterpreterV1.DATA_INSUFFICIENT)) {
            return PolicyDslInterpreterV1.DATA_INSUFFICIENT;
        }
        if (outcomes.contains(PolicyDslInterpreterV1.REFER)) {
            return PolicyDslInterpreterV1.REFER;
        }
        if (outcomes.stream().allMatch(PolicyDslInterpreterV1.PASS::equals)) {
            return PolicyDslInterpreterV1.PASS;
        }
        return PolicyDslInterpreterV1.PASS;
    }

    /**
     * Default precedence: KNOCKOUT FAIL > HARD FAIL > REFER > DI > PASS
     */
    private String aggregateOverall(Map<String, Object> content, List<CiStandardRuleResult> results) {
        List<String> precedence = precedenceList(content);
        for (String pref : precedence) {
            for (CiStandardRuleResult r : results) {
                String key = r.getRuleType() + ":" + r.getOutcome();
                if (pref.equals(key) || pref.equals(r.getOutcome())) {
                    if (pref.endsWith("FAIL") && PolicyDslInterpreterV1.FAIL.equals(r.getOutcome())) {
                        return PolicyDslInterpreterV1.FAIL;
                    }
                    if (PolicyDslInterpreterV1.REFER.equals(pref) && PolicyDslInterpreterV1.REFER.equals(r.getOutcome())) {
                        return PolicyDslInterpreterV1.REFER;
                    }
                    if ((PolicyDslInterpreterV1.DATA_INSUFFICIENT.equals(pref) || "DI".equals(pref))
                            && PolicyDslInterpreterV1.DATA_INSUFFICIENT.equals(r.getOutcome())) {
                        return PolicyDslInterpreterV1.DATA_INSUFFICIENT;
                    }
                }
            }
        }
        boolean anyKnockoutFail = results.stream().anyMatch(r ->
                PolicyRuleType.KNOCKOUT.name().equals(r.getRuleType())
                        && PolicyDslInterpreterV1.FAIL.equals(r.getOutcome()));
        if (anyKnockoutFail) {
            return PolicyDslInterpreterV1.FAIL;
        }
        boolean anyHardFail = results.stream().anyMatch(r ->
                PolicyRuleType.HARD.name().equals(r.getRuleType())
                        && PolicyDslInterpreterV1.FAIL.equals(r.getOutcome()));
        if (anyHardFail) {
            return PolicyDslInterpreterV1.FAIL;
        }
        boolean anyFail = results.stream().anyMatch(r -> PolicyDslInterpreterV1.FAIL.equals(r.getOutcome()));
        if (anyFail) {
            return PolicyDslInterpreterV1.FAIL;
        }
        boolean anyRefer = results.stream().anyMatch(r -> PolicyDslInterpreterV1.REFER.equals(r.getOutcome()));
        if (anyRefer) {
            return PolicyDslInterpreterV1.REFER;
        }
        boolean anyDi = results.stream().anyMatch(r ->
                PolicyDslInterpreterV1.DATA_INSUFFICIENT.equals(r.getOutcome()));
        if (anyDi) {
            return PolicyDslInterpreterV1.DATA_INSUFFICIENT;
        }
        return PolicyDslInterpreterV1.PASS;
    }

    @SuppressWarnings("unchecked")
    private List<String> precedenceList(Map<String, Object> content) {
        if (content.get("outcomePrecedence") instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        return List.of(
                "KNOCKOUT:FAIL",
                "HARD:FAIL",
                "FAIL",
                "REFER",
                "DATA_INSUFFICIENT",
                "PASS");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> indexRules(Map<String, Object> content) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (!(content.get("rules") instanceof List<?> rules)) {
            return out;
        }
        for (Object r : rules) {
            if (!(r instanceof Map<?, ?> rm)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) rm;
            String id = String.valueOf(rule.getOrDefault("ruleId", rule.get("id")));
            out.put(id, rule);
        }
        return out;
    }

    private PolicyRuleType parseRuleType(Object o) {
        if (o == null) {
            return PolicyRuleType.HARD;
        }
        try {
            return PolicyRuleType.valueOf(String.valueOf(o).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PolicyRuleType.HARD;
        }
    }

    private Map<String, Object> scoreToMap(CiScoreResult score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scorecardCode", score.getScorecardCode());
        m.put("score", score.getScore());
        m.put("grade", score.getGrade());
        m.put("componentResults", score.getComponentResults());
        m.put("weightUsed", score.getWeightUsed());
        m.put("weightUnavailable", score.getWeightUnavailable());
        m.put("dataCompleteness", score.getDataCompleteness());
        m.put("reasonCodes", score.getReasonCodes());
        return m;
    }
}
