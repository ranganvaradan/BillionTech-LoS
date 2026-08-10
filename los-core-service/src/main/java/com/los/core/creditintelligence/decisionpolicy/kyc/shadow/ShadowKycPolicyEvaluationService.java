package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyStages;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycReferPayload;
import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * KYC-5 — evaluates Decision Policy KYC & Eligibility rules in SHADOW only.
 * Reuses PolicyDslInterpreterV1. Never mutates production KYC / UW.
 * authoritative=false, allowCanonicalAuthority=false.
 */
@Slf4j
@Service
public class ShadowKycPolicyEvaluationService {

    public static final String ENGINE = "SHADOW_KYC_POLICY_V1";
    public static final String CERT_SHADOW_READY = "SHADOW_EVALUATION_READY";
    public static final String CERT_BLOCKED = "BLOCKED";
    public static final String CERT_INSUFFICIENT = "INSUFFICIENT_EVIDENCE";

    private final PolicyDslInterpreterV1 interpreter;
    private final ContentHasher hasher;
    private final ObjectProvider<CiKycPolicyEvaluationRepository> repository;

    public ShadowKycPolicyEvaluationService(
            ObjectProvider<CiKycPolicyEvaluationRepository> repository
    ) {
        this(new PolicyDslInterpreterV1(), new ContentHasher(), repository);
    }

    public ShadowKycPolicyEvaluationService() {
        this(new PolicyDslInterpreterV1(), new ContentHasher(), null);
    }

    public ShadowKycPolicyEvaluationService(
            PolicyDslInterpreterV1 interpreter,
            ContentHasher hasher,
            ObjectProvider<CiKycPolicyEvaluationRepository> repository
    ) {
        this.interpreter = interpreter == null ? new PolicyDslInterpreterV1() : interpreter;
        this.hasher = hasher == null ? new ContentHasher() : hasher;
        this.repository = repository;
    }

    /**
     * Evaluate KYC-only rules against frozen facts. Does not call providers.
     */
    public Map<String, Object> evaluate(ShadowKycEvaluationRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shadow", true);
        out.put("authoritative", false);
        out.put("allowCanonicalAuthority", false);
        out.put("engine", ENGINE);

        if (request == null || request.policyPackage() == null) {
            out.put("status", CERT_BLOCKED);
            out.put("routingOutcome", "UNLINKED");
            out.put("overallOutcome", KycBusinessOutcome.MISSING_INFORMATION.name());
            out.put("reason", "No immutable Decision Policy package supplied");
            out.put("certificationStatus", CERT_BLOCKED);
            return out;
        }

        CiExecutablePolicyPackage pkg = request.policyPackage();
        String routingOutcome = request.routingOutcome() == null
                ? "EXACTLY_ONE" : request.routingOutcome();
        if ("NO_APPLICABLE_POLICY".equals(routingOutcome)
                || "AMBIGUOUS_POLICY_CONFIGURATION".equals(routingOutcome)
                || "UNLINKED".equals(routingOutcome)
                || "NOT_ELIGIBLE_FOR_SHADOW_ROUTING".equals(routingOutcome)) {
            out.put("status", CERT_BLOCKED);
            out.put("routingOutcome", routingOutcome);
            out.put("overallOutcome", KycBusinessOutcome.MISSING_INFORMATION.name());
            out.put("reason", request.routingReason() == null
                    ? routingOutcome : request.routingReason());
            out.put("certificationStatus", CERT_BLOCKED);
            out.put("policyVersion", pkg.getVersion());
            out.put("policyCode", pkg.getPolicyCode());
            return out;
        }

        Map<String, Object> frozenFacts = request.frozenFacts() != null
                ? new LinkedHashMap<>(request.frozenFacts())
                : NormalizedKycFactBuilder.buildFacts(
                request.stepEvidence(),
                request.applicationHints(),
                request.productionKycOutcome());

        Map<String, Object> appFields = request.applicationFields() == null
                ? Map.of() : Map.copyOf(request.applicationFields());

        Instant clockInstant = request.evaluationInstant() == null
                ? Instant.parse("2024-06-15T00:00:00Z")
                : request.evaluationInstant();
        var clock = new FixedEvaluationClock(clockInstant, ZoneId.of("Asia/Kolkata"));

        List<Map<String, Object>> kycRules = selectKycEligibilityRules(pkg);
        if (kycRules.isEmpty()) {
            out.put("status", CERT_INSUFFICIENT);
            out.put("routingOutcome", routingOutcome);
            out.put("overallOutcome", KycBusinessOutcome.MISSING_INFORMATION.name());
            out.put("reason", "Resolved package has no KYC/ELIGIBILITY rules");
            out.put("certificationStatus", CERT_INSUFFICIENT);
            out.put("frozenFacts", frozenFacts);
            return out;
        }

        PolicyDslInterpreterV1.EvaluationContext dslCtx = PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of(),
                frozenFacts,
                Map.of(),
                appFields,
                clock,
                PolicyDslInterpreterV1.DATA_INSUFFICIENT);

        List<Map<String, Object>> ruleResults = new ArrayList<>();
        List<Object> evidenceRefs = new ArrayList<>(
                request.evidenceRefs() == null ? List.of() : request.evidenceRefs());

        for (Map<String, Object> rule : kycRules) {
            ruleResults.add(evaluateOneRule(rule, dslCtx, frozenFacts));
        }

        KycPolicyOutcomeAggregator.Aggregate agg = KycPolicyOutcomeAggregator.aggregate(ruleResults);
        boolean providerUnavailable = detectProviderUnavailable(frozenFacts, request.stepEvidence());

        KycShadowComparisonClassifier.Comparison comparison = KycShadowComparisonClassifier.compare(
                request.productionKycOutcome(),
                agg.overall(),
                providerUnavailable);

        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("policyContentHash", pkg.getContentHash());
        hashPayload.put("policyCode", pkg.getPolicyCode());
        hashPayload.put("policyVersion", pkg.getVersion());
        hashPayload.put("frozenFacts", frozenFacts);
        hashPayload.put("applicationFields", appFields);
        hashPayload.put("ruleOutcomes", ruleResults.stream()
                .map(r -> r.get("ruleId") + ":" + r.get("outcome")).toList());
        hashPayload.put("overall", agg.overall().name());
        hashPayload.put("businessDate", request.evaluationBusinessDate() == null
                ? null : request.evaluationBusinessDate().toString());
        String detHash = hasher.hashMap(hashPayload);

        Map<String, Object> referPayload = Map.of();
        if (agg.overall() == KycBusinessOutcome.REFER && !agg.referReasons().isEmpty()) {
            referPayload = KycReferPayload.fromStep(
                    "KYC_SHADOW_REFER",
                    String.valueOf(ruleResults.stream()
                            .filter(r -> "REFER".equals(String.valueOf(r.get("outcome"))))
                            .map(r -> r.get("ruleId")).findFirst().orElse("KYC")),
                    "SHADOW_KYC",
                    String.join("; ", agg.referReasons())).toMap();
            if (pkg.getId() != null) {
                referPayload = new LinkedHashMap<>(referPayload);
                referPayload.put("policyVersionId", pkg.getId().toString());
                referPayload.put("policyVersion", pkg.getVersion());
                referPayload.put("reviewStatus", "SHADOW_ONLY");
                referPayload.put("shadow", true);
            }
        }

        Map<String, Object> workflowProv = request.workflowProvenance() == null
                ? Map.of("available", false, "note", "Workflow provenance not supplied — full evidence replay not claimed")
                : new LinkedHashMap<>(request.workflowProvenance());
        if (!workflowProv.containsKey("available")) {
            workflowProv = new LinkedHashMap<>(workflowProv);
            workflowProv.put("available", true);
        }

        String cert = CERT_SHADOW_READY;
        if (comparison.reviewRequired()) {
            cert = CERT_SHADOW_READY; // still shadow-ready, but flagged
        }
        if (request.stepEvidence() == null || request.stepEvidence().isEmpty()) {
            if (agg.overall() == KycBusinessOutcome.MISSING_INFORMATION) {
                cert = CERT_INSUFFICIENT;
            }
        }

        out.put("status", "COMPLETED");
        out.put("routingOutcome", routingOutcome);
        out.put("overallOutcome", agg.overall().name());
        out.put("dslOverallOutcome", agg.overall().toDslOutcome());
        out.put("productionKycOutcome", request.productionKycOutcome());
        out.put("comparisonClass", comparison.classification());
        out.put("comparison", comparison.detail());
        out.put("reviewRequired", comparison.reviewRequired());
        out.put("reviewReason", comparison.reviewReason());
        out.put("rootCauseCategory", comparison.rootCauseCategory());
        out.put("ruleResults", ruleResults);
        out.put("frozenFacts", frozenFacts);
        out.put("applicationInputs", appFields);
        out.put("missingFacts", agg.missingFacts());
        out.put("failReasons", agg.failReasons());
        out.put("referReasons", agg.referReasons());
        out.put("referPayload", referPayload);
        out.put("evidenceRefs", evidenceRefs);
        out.put("aggregateSemantics", KycPolicyOutcomeAggregator.documentedSemantics());
        out.put("policyCode", pkg.getPolicyCode());
        out.put("policyVersion", pkg.getVersion());
        out.put("policyPackageId", pkg.getId());
        out.put("policyContentHash", pkg.getContentHash());
        out.put("evaluationBusinessDate", request.evaluationBusinessDate());
        out.put("deterministicHash", detHash);
        out.put("workflowProvenance", workflowProv);
        out.put("providerCallsMade", false);
        out.put("certificationStatus", cert);
        out.put("demoLabel", extractDemoLabel(pkg));
        out.put("banner", "SHADOW — DOES NOT AFFECT APPLICATION");

        if (request.persist() && repository != null) {
            try {
                CiKycPolicyEvaluationRepository repo = repository.getIfAvailable();
                if (repo != null && request.applicationId() != null) {
                    CiKycPolicyEvaluation entity = toEntity(request, pkg, routingOutcome, agg,
                            comparison, frozenFacts, appFields, ruleResults, referPayload,
                            evidenceRefs, workflowProv, detHash, cert);
                    repo.save(entity);
                    out.put("persistedEvaluationId", entity.getId());
                }
            } catch (Exception ex) {
                log.warn("kyc_shadow_persist_failed app={} err={}",
                        request.applicationId(), ex.getMessage());
                out.put("persistError", ex.getClass().getSimpleName());
            }
        }

        return out;
    }

    /** Deterministic replay using the same frozen payload. */
    public Map<String, Object> replay(Map<String, Object> priorResult, CiExecutablePolicyPackage pkg) {
        if (priorResult == null || pkg == null) {
            return Map.of("replayMatch", false, "reason", "missing prior or package");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = priorResult.get("frozenFacts") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> app = priorResult.get("applicationInputs") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        LocalDate date = null;
        if (priorResult.get("evaluationBusinessDate") != null) {
            date = LocalDate.parse(String.valueOf(priorResult.get("evaluationBusinessDate")));
        }
        ShadowKycEvaluationRequest req = ShadowKycEvaluationRequest.builder()
                .tenantId(pkg.getTenantId())
                .applicationId(null)
                .policyPackage(pkg)
                .routingOutcome("EXACTLY_ONE")
                .frozenFacts(facts)
                .applicationFields(app)
                .productionKycOutcome(String.valueOf(priorResult.get("productionKycOutcome")))
                .evaluationBusinessDate(date)
                .persist(false)
                .build();
        Map<String, Object> again = evaluate(req);
        boolean match = String.valueOf(priorResult.get("deterministicHash"))
                .equals(String.valueOf(again.get("deterministicHash")))
                && String.valueOf(priorResult.get("overallOutcome"))
                .equals(String.valueOf(again.get("overallOutcome")));
        Map<String, Object> out = new LinkedHashMap<>(again);
        out.put("replayMatch", match);
        out.put("priorHash", priorResult.get("deterministicHash"));
        out.put("replayHash", again.get("deterministicHash"));
        out.put("targetReplayMatchPercent", 100);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> selectKycEligibilityRules(CiExecutablePolicyPackage pkg) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (pkg == null || pkg.getContent() == null) {
            return out;
        }
        Object rulesObj = pkg.getContent().get("rules");
        if (!(rulesObj instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) raw;
            if (isKycEligibilityRule(rule)) {
                out.add(rule);
            }
        }
        return out;
    }

    public static boolean isKycEligibilityRule(Map<String, Object> rule) {
        if (rule == null) {
            return false;
        }
        Object domain = rule.get("decisionDomain");
        if (domain != null) {
            DecisionPolicyDomain d = DecisionPolicyDomain.fromMetadata(domain);
            return d.isKycOrEligibility();
        }
        String stage = String.valueOf(rule.getOrDefault("stageCode", ""));
        return DecisionPolicyStages.isKycEligibilityStage(stage);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateOneRule(
            Map<String, Object> rule,
            PolicyDslInterpreterV1.EvaluationContext baseCtx,
            Map<String, Object> facts
    ) {
        String ruleId = String.valueOf(rule.getOrDefault("ruleId", "RULE"));
        String onMissing = rule.get("onMissing") == null
                ? PolicyDslInterpreterV1.DATA_INSUFFICIENT
                : String.valueOf(rule.get("onMissing"));
        Map<String, Object> expr = rule.get("expression") instanceof Map<?, ?> em
                ? (Map<String, Object>) em : Map.of();

        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(
                baseCtx.metrics(), baseCtx.facts(), baseCtx.policyParameters(),
                baseCtx.applicationFields(), baseCtx.clock(), onMissing, baseCtx.reconciliations());

        String dslOutcome;
        if (expr.isEmpty()) {
            dslOutcome = onMissing;
        } else {
            dslOutcome = interpreter.evaluate(expr, ctx);
            if (PolicyDslInterpreterV1.PASS.equals(dslOutcome)) {
                dslOutcome = rule.get("onTrue") == null ? PolicyDslInterpreterV1.PASS
                        : String.valueOf(rule.get("onTrue"));
            } else if (PolicyDslInterpreterV1.FAIL.equals(dslOutcome)) {
                dslOutcome = rule.get("onFalse") == null ? PolicyDslInterpreterV1.FAIL
                        : String.valueOf(rule.get("onFalse"));
            }
        }

        // Manual verification with explicit name mismatch already returns REFER via DSL IFF.
        // Do NOT convert absent facts (DATA_INSUFFICIENT) into REFER — that would hide
        // mandatory MISSING_INFORMATION (e.g. provider outage / missing PAN).

        String reqType = String.valueOf(rule.getOrDefault("kycRequirementType", ""));
        KycBusinessOutcome biz = KycPolicyOutcomeAggregator.fromDsl(dslOutcome);
        List<String> missing = new ArrayList<>();
        if (biz == KycBusinessOutcome.MISSING_INFORMATION) {
            Object refs = rule.get("inputRefs");
            if (refs instanceof List<?> list) {
                for (Object r : list) {
                    if (r instanceof Map<?, ?> rm && "FACT".equalsIgnoreCase(String.valueOf(rm.get("kind")))) {
                        String ref = String.valueOf(rm.get("reference"));
                        if (!facts.containsKey(ref)) {
                            missing.add(ref);
                        }
                    }
                }
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        row.put("businessRuleName", rule.getOrDefault("businessRuleName", ruleId));
        row.put("requirementType", reqType);
        row.put("domain", rule.getOrDefault("decisionDomain", "KYC"));
        row.put("ruleType", rule.getOrDefault("ruleType", "HARD"));
        row.put("guardrailClass", rule.get("guardrailClass"));
        row.put("mandatory", rule.getOrDefault("mandatory", true));
        row.put("outcome", dslOutcome);
        row.put("businessOutcome", biz.name());
        row.put("missingFacts", missing);
        row.put("manualReviewRequired", PolicyDslInterpreterV1.REFER.equals(dslOutcome)
                || "MANUAL_VERIFICATION".equals(reqType));
        row.put("reason", rule.getOrDefault("businessRuleName", ruleId));
        row.put("technicalSourceStatus", technicalForRule(rule, facts));
        row.put("shadow", true);
        row.put("authoritative", false);
        return row;
    }

    private static String technicalForRule(Map<String, Object> rule, Map<String, Object> facts) {
        Object refs = rule.get("inputRefs");
        if (!(refs instanceof List<?> list) || list.isEmpty()) {
            return "UNKNOWN";
        }
        for (Object r : list) {
            if (r instanceof Map<?, ?> rm && "FACT".equalsIgnoreCase(String.valueOf(rm.get("kind")))) {
                String ref = String.valueOf(rm.get("reference"));
                String prefix = ref.contains(".") ? ref.substring(0, ref.lastIndexOf('.')) : ref;
                Object tech = facts.get(prefix + ".technical_status");
                if (tech != null) {
                    return String.valueOf(tech);
                }
            }
        }
        return "UNKNOWN";
    }

    private static boolean detectProviderUnavailable(
            Map<String, Object> facts,
            List<NormalizedKycFactBuilder.StepEvidence> steps
    ) {
        if (facts != null) {
            for (Map.Entry<String, Object> e : facts.entrySet()) {
                if (e.getKey().endsWith(".technical_status")) {
                    String v = String.valueOf(e.getValue()).toUpperCase(Locale.ROOT);
                    if (v.contains("UNAVAILABLE") || v.contains("TIMEOUT") || v.contains("ERROR")) {
                        return true;
                    }
                }
            }
        }
        if (steps != null) {
            for (NormalizedKycFactBuilder.StepEvidence s : steps) {
                if (s == null || s.outcome() == null) {
                    continue;
                }
                if (s.outcome() == StepOutcome.ERROR) {
                    return true;
                }
                String err = s.errorMessage() == null ? "" : s.errorMessage().toLowerCase(Locale.ROOT);
                if (err.contains("unavailable") || err.contains("timeout") || err.contains("provider")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String extractDemoLabel(CiExecutablePolicyPackage pkg) {
        if (pkg.getApprovalMetadata() != null && pkg.getApprovalMetadata().get("demoLabel") != null) {
            return String.valueOf(pkg.getApprovalMetadata().get("demoLabel"));
        }
        if (pkg.getContent() != null && pkg.getContent().get("metadata") instanceof Map<?, ?> m
                && m.get("demoLabel") != null) {
            return String.valueOf(m.get("demoLabel"));
        }
        return null;
    }

    private static CiKycPolicyEvaluation toEntity(
            ShadowKycEvaluationRequest request,
            CiExecutablePolicyPackage pkg,
            String routingOutcome,
            KycPolicyOutcomeAggregator.Aggregate agg,
            KycShadowComparisonClassifier.Comparison comparison,
            Map<String, Object> facts,
            Map<String, Object> appFields,
            List<Map<String, Object>> ruleResults,
            Map<String, Object> referPayload,
            List<Object> evidenceRefs,
            Map<String, Object> workflowProv,
            String detHash,
            String cert
    ) {
        return CiKycPolicyEvaluation.builder()
                .id(UUID.randomUUID())
                .tenantId(request.tenantId())
                .applicationId(request.applicationId())
                .evaluationBusinessDate(request.evaluationBusinessDate())
                .policyVersionId(pkg.getId())
                .executablePackageId(pkg.getId())
                .policyCode(pkg.getPolicyCode())
                .policyVersion(pkg.getVersion())
                .policyContentHash(pkg.getContentHash())
                .routingOutcome(routingOutcome)
                .overallOutcome(agg.overall().name())
                .productionKycOutcome(request.productionKycOutcome())
                .comparisonClass(comparison.classification())
                .reviewRequired(comparison.reviewRequired())
                .reviewReason(comparison.reviewReason())
                .deterministicHash(detHash)
                .frozenFacts(facts)
                .applicationInputs(appFields)
                .ruleResults(new ArrayList<>(ruleResults))
                .comparison(comparison.detail())
                .referPayload(referPayload)
                .workflowProvenance(workflowProv)
                .evidenceRefs(evidenceRefs)
                .missingFacts(new ArrayList<>(agg.missingFacts()))
                .failReasons(new ArrayList<>(agg.failReasons()))
                .referReasons(new ArrayList<>(agg.referReasons()))
                .authoritative(false)
                .shadow(true)
                .certificationStatus(cert)
                .createdAt(Instant.now())
                .build();
    }

    /** Convenience: build step evidence for tests without JPA entities. */
    public static NormalizedKycFactBuilder.StepEvidence step(
            KycStepType type, StepOutcome outcome, String error, Map<String, Object> parsed, boolean fallback
    ) {
        return new NormalizedKycFactBuilder.StepEvidence(type, outcome, error, parsed, fallback, null);
    }

    public static NormalizedKycFactBuilder.StepEvidence panPass() {
        return new NormalizedKycFactBuilder.StepEvidence(
                KycStepType.PAN_VERIFY, StepOutcome.SUCCESS, null, Map.of("nameMatch", true), false, true);
    }

    public static NormalizedKycFactBuilder.StepEvidence panFail() {
        return step(KycStepType.PAN_VERIFY, StepOutcome.FAILURE, "PAN invalid", Map.of(), false);
    }

    public static NormalizedKycFactBuilder.StepEvidence panProviderOutage() {
        return step(KycStepType.PAN_VERIFY, StepOutcome.ERROR, "provider unavailable timeout", Map.of(), false);
    }
}
