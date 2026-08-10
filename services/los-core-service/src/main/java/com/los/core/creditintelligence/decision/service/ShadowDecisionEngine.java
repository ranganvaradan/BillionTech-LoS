package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.domain.RecommendationOutcome;
import com.los.core.creditintelligence.decision.domain.RecommendationStatus;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * P2 Shadow Decision Engine — recommendation only.
 * Always authoritative=false; never writes CiHumanCreditDecision.
 */
@Service
public class ShadowDecisionEngine {

    public static final String ENGINE_VERSION = "P2_SHADOW_V1";

    private final LimitMethodEngine limitEngine;
    private final TenureEngine tenureEngine;
    private final PricingEngine pricingEngine;
    private final CollateralEngine collateralEngine;
    private final ConditionEngine conditionEngine;
    private final CovenantEngine covenantEngine;
    private final AuthorityMatrixEngine authorityEngine;
    private final DeviationIdentifier deviationIdentifier;
    private final DecisionRecommendationExplanationBuilder explanationBuilder;
    private final ContentHasher hasher;

    public ShadowDecisionEngine() {
        this(new LimitMethodEngine(), new TenureEngine(), new PricingEngine(), new CollateralEngine(),
                new ConditionEngine(), new CovenantEngine(), new AuthorityMatrixEngine(),
                new DeviationIdentifier(), new DecisionRecommendationExplanationBuilder(), new ContentHasher());
    }

    public ShadowDecisionEngine(
            LimitMethodEngine limitEngine,
            TenureEngine tenureEngine,
            PricingEngine pricingEngine,
            CollateralEngine collateralEngine,
            ConditionEngine conditionEngine,
            CovenantEngine covenantEngine,
            AuthorityMatrixEngine authorityEngine,
            DeviationIdentifier deviationIdentifier,
            DecisionRecommendationExplanationBuilder explanationBuilder,
            ContentHasher hasher) {
        this.limitEngine = limitEngine != null ? limitEngine : new LimitMethodEngine();
        this.tenureEngine = tenureEngine != null ? tenureEngine : new TenureEngine();
        this.pricingEngine = pricingEngine != null ? pricingEngine : new PricingEngine();
        this.collateralEngine = collateralEngine != null ? collateralEngine : new CollateralEngine();
        this.conditionEngine = conditionEngine != null ? conditionEngine : new ConditionEngine();
        this.covenantEngine = covenantEngine != null ? covenantEngine : new CovenantEngine();
        this.authorityEngine = authorityEngine != null ? authorityEngine : new AuthorityMatrixEngine();
        this.deviationIdentifier = deviationIdentifier != null ? deviationIdentifier : new DeviationIdentifier();
        this.explanationBuilder = explanationBuilder != null ? explanationBuilder
                : new DecisionRecommendationExplanationBuilder();
        this.hasher = hasher != null ? hasher : new ContentHasher();
    }

    public CiCreditRecommendation recommend(CiDecisionStrategy strategy, DecisionRuntimeInput input) {
        if (strategy == null || input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "strategy and input required");
        }
        if (strategy.getTenantId() == null || input.tenantId() == null
                || !strategy.getTenantId().equals(input.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant decision evaluation rejected");
        }

        UUID recommendationId = UUID.randomUUID();
        Map<String, Object> content = strategy.getContent() == null ? Map.of() : strategy.getContent();
        Map<String, Object> dimensions = new LinkedHashMap<>();
        List<String> reasonCodes = new ArrayList<>();
        List<Object> limitations = new ArrayList<>();
        List<Object> evidenceRefs = new ArrayList<>();

        evidenceRefs.add(Map.of("kind", "POLICY_EVALUATION", "reference",
                input.policyEvaluationId() == null ? "" : input.policyEvaluationId().toString()));
        evidenceRefs.add(Map.of("kind", "EVALUATION_CONTEXT", "reference",
                input.evaluationContextId() == null ? "" : input.evaluationContextId().toString()));

        String mapped = mapPolicyOutcome(content, input);
        dimensions.put("Eligibility", eligibilityDimension(input, mapped));
        dimensions.put("RiskGrade", riskGradeDimension(input));

        // Banking BRE helper: PASS → ELIGIBLE; missing amount params → limitation
        applyBankingBureauHelpers(input, content, dimensions, limitations, reasonCodes);

        DeviationIdentifier.DeviationResult deviations =
                deviationIdentifier.identify(input, input.tenantId());

        boolean earlyStop = isTerminal(mapped);
        LimitMethodEngine.LimitResult limits = null;
        TenureEngine.TenureResult tenure = null;
        PricingEngine.PricingResult pricing = null;
        CollateralEngine.CollateralResult collateral = null;
        ConditionEngine.ConditionResult conditions = null;
        CovenantEngine.CovenantResult covenants = null;
        AuthorityMatrixEngine.AuthorityResult authority = null;

        RecommendationOutcome outcome;
        if (earlyStop) {
            outcome = RecommendationOutcome.valueOf(mapped);
            reasonCodes.add("POLICY_OUTCOME:" + mapped);
            // Partial dimensions still computed where cheap
            tenure = tenureEngine.compute(DecisionValueHelper.asMap(content.get("tenureStrategy")), input);
            dimensions.put("Tenure", tenure.detail());
            pricing = pricingEngine.compute(DecisionValueHelper.asMap(content.get("pricingStrategy")),
                    input, tenure.recommendedTenure());
            dimensions.put("Pricing", pricing.detail());
            limitations.addAll(pricing.limitations());
            conditions = conditionEngine.compute(DecisionValueHelper.asMap(content.get("conditionStrategy")),
                    input, null, RecommendationOutcome.DATA_INSUFFICIENT == outcome);
            dimensions.put("Conditions", Map.of("count", conditions.conditions().size()));
            dimensions.put("Limit", Map.of("skipped", true, "reason", "EARLY_POLICY_STOP"));
            dimensions.put("Collateral", Map.of("skipped", true));
            dimensions.put("Covenants", Map.of("skipped", true));
            authority = authorityEngine.compute(DecisionValueHelper.asMap(content.get("authorityStrategy")),
                    input, input.requestedAmount(), deviations.deviations().size(),
                    deviations.materialCount() > 0);
            dimensions.put("Authority", authority.detail());
        } else {
            // CONTINUE structuring
            limits = limitEngine.compute(DecisionValueHelper.asMap(content.get("limitStrategy")), input);
            dimensions.put("Limit", limits.detail());
            reasonCodes.addAll(limits.reasonCodes());

            tenure = tenureEngine.compute(DecisionValueHelper.asMap(content.get("tenureStrategy")), input);
            dimensions.put("Tenure", tenure.detail());
            reasonCodes.addAll(tenure.reasonCodes());

            pricing = pricingEngine.compute(DecisionValueHelper.asMap(content.get("pricingStrategy")),
                    input, tenure.recommendedTenure());
            dimensions.put("Pricing", pricing.detail());
            reasonCodes.addAll(pricing.reasonCodes());
            limitations.addAll(pricing.limitations());

            collateral = collateralEngine.compute(DecisionValueHelper.asMap(content.get("collateralStrategy")),
                    input, limits.recommendedAmount());
            dimensions.put("Collateral", collateral.detail());
            reasonCodes.addAll(collateral.reasonCodes());

            boolean di = limits.amountOutcome() == RecommendationOutcome.DATA_INSUFFICIENT;
            conditions = conditionEngine.compute(DecisionValueHelper.asMap(content.get("conditionStrategy")),
                    input, collateral, di);
            dimensions.put("Conditions", Map.of(
                    "precedentCount", conditions.conditionsPrecedent().size(),
                    "subsequentCount", conditions.conditionsSubsequent().size()));
            reasonCodes.addAll(conditions.reasonCodes());

            Map<String, Object> limitParams = DecisionValueHelper.asMap(
                    DecisionValueHelper.asMap(content.get("limitStrategy")).get("params"));
            covenants = covenantEngine.compute(DecisionValueHelper.asMap(content.get("covenantStrategy")), limitParams);
            dimensions.put("Covenants", Map.of("count", covenants.covenants().size()));
            reasonCodes.addAll(covenants.reasonCodes());

            authority = authorityEngine.compute(DecisionValueHelper.asMap(content.get("authorityStrategy")),
                    input, limits.recommendedAmount(), deviations.deviations().size(),
                    deviations.materialCount() > 0);
            dimensions.put("Authority", authority.detail());
            reasonCodes.addAll(authority.reasonCodes());
            reasonCodes.addAll(deviations.reasonCodes());

            outcome = resolveStructuringOutcome(limits, conditions, collateral, pricing, content);
        }

        BigDecimal recommendedAmount = limits != null ? limits.recommendedAmount() : null;
        Integer recommendedTenure = tenure != null ? tenure.recommendedTenure() : input.requestedTenureMonths();
        BigDecimal finalRate = pricing != null ? pricing.finalRate() : null;
        BigDecimal baseRate = pricing != null ? pricing.baseRate() : null;
        BigDecimal riskPremium = pricing != null ? pricing.riskPremium() : null;

        BigDecimal emi = null;
        if (recommendedAmount != null && finalRate != null && recommendedTenure != null) {
            String amort = String.valueOf(DecisionValueHelper.asMap(
                    DecisionValueHelper.asMap(content.get("limitStrategy")).get("params"))
                    .getOrDefault("amortizationVersion", "EMI_FLAT_V1"));
            emi = limitEngine.emiFromPrincipal(recommendedAmount, finalRate, recommendedTenure, amort);
        }

        Map<String, Object> reasonGraph = buildReasonGraph(outcome, limits, tenure, pricing, conditions);
        Map<String, Object> finalDim = new LinkedHashMap<>();
        finalDim.put("outcome", outcome.name());
        finalDim.put("authoritative", false);
        finalDim.put("shadowOnly", true);
        finalDim.put("humanReviewRequired", true);
        dimensions.put("FinalRecommendation", finalDim);

        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("outcome", outcome.name());
        hashPayload.put("amount", recommendedAmount);
        hashPayload.put("tenure", recommendedTenure);
        hashPayload.put("pricing", finalRate);
        hashPayload.put("conditions", conditions == null ? List.of() : conditions.conditionsPrecedent());
        hashPayload.put("collateral", collateral == null ? Map.of() : collateral.collateral());
        hashPayload.put("authority", authority == null ? null : authority.level());
        hashPayload.put("reasonCodes", reasonCodes);
        hashPayload.put("strategyHash", strategy.getContentHash());
        String detHash = hasher.hashMap(hashPayload);

        CiCreditRecommendation rec = CiCreditRecommendation.builder()
                .id(recommendationId)
                .tenantId(input.tenantId())
                .applicationId(input.applicationId())
                .decisionStrategyId(strategy.getId())
                .recommendationVersion(strategy.getVersion() == null ? "1" : strategy.getVersion())
                .recommendationOutcome(outcome.name())
                .recommendedFacilityType(DecisionValueHelper.str(
                        input.applicationFields().getOrDefault("facilityType",
                                input.productCode() == null ? "TERM_LOAN" : input.productCode())))
                .recommendedAmount(recommendedAmount)
                .recommendedTenureMonths(recommendedTenure)
                .recommendedRepaymentFrequency("MONTHLY")
                .recommendedEmi(emi)
                .recommendedBaseRate(baseRate)
                .recommendedRiskPremium(riskPremium)
                .recommendedFinalRate(finalRate)
                .recommendedProcessingFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .recommendedCollateral(collateral == null ? Map.of() : collateral.collateral())
                .recommendedLtv(collateral == null ? null : collateral.recommendedLtv())
                .recommendedGuarantors(List.of())
                .conditionsPrecedent(conditions == null ? List.of() : conditions.conditionsPrecedent())
                .conditionsSubsequent(conditions == null ? List.of() : conditions.conditionsSubsequent())
                .covenants(covenants == null ? List.of() : covenants.covenantPayload())
                .approvalAuthorityLevel(authority == null ? null : authority.level())
                .humanReviewRequired(true)
                .reasonCodes(new ArrayList<>(reasonCodes))
                .explanation(new LinkedHashMap<>())
                .evidenceRefs(evidenceRefs)
                .confidence(BigDecimal.valueOf(earlyStop ? 0.55 : 0.80))
                .limitations(limitations)
                .status(RecommendationStatus.RECOMMENDED.name())
                .authoritative(false)
                .deterministicDecisionHash(detHash)
                .dimensions(dimensions)
                .limitMethodResults(limits == null ? List.of() : limits.candidates())
                .pricingComponentResults(pricing == null ? List.of() : pricing.components())
                .conditionRecommendations(conditions == null ? List.of() : conditions.conditions())
                .covenantRecommendations(covenants == null ? List.of() : covenants.covenants())
                .deviations(deviations.deviations())
                .authorityDetail(authority == null ? Map.of() : authority.detail())
                .build();

        // stamp child recommendation ids
        if (rec.getLimitMethodResults() != null) {
            rec.getLimitMethodResults().forEach(r -> r.setRecommendationId(recommendationId));
        }
        if (rec.getPricingComponentResults() != null) {
            rec.getPricingComponentResults().forEach(r -> r.setRecommendationId(recommendationId));
        }
        if (rec.getConditionRecommendations() != null) {
            rec.getConditionRecommendations().forEach(r -> r.setRecommendationId(recommendationId));
        }
        if (rec.getCovenantRecommendations() != null) {
            rec.getCovenantRecommendations().forEach(r -> r.setRecommendationId(recommendationId));
        }
        if (rec.getDeviations() != null) {
            rec.getDeviations().forEach(d -> {
                d.setRecommendationId(recommendationId);
                // P2 never auto-approves
                if ("APPROVED".equals(d.getStatus())) {
                    d.setStatus("REQUESTED");
                }
            });
        }

        Map<String, Object> explanation = explanationBuilder.build(rec, reasonGraph);
        explanation.put("reasonGraph", reasonGraph);
        explanation.put("engineVersion", ENGINE_VERSION);
        explanation.put("shadowOnly", true);
        explanation.put("authoritative", false);
        explanation.put("aiAuthority", false);
        rec.setExplanation(explanation);
        return rec;
    }

    private String mapPolicyOutcome(Map<String, Object> content, DecisionRuntimeInput input) {
        Map<String, Object> mapping = DecisionValueHelper.asMap(content.get("policyOutcomeMapping"));
        String overall = input.policyOverallOutcome() == null ? "PASS" : input.policyOverallOutcome().toUpperCase(Locale.ROOT);

        // Knockout / hard fail detection from rule results
        boolean knockoutFail = false;
        boolean hardFail = false;
        for (Map<String, Object> rule : input.policyRuleResults()) {
            String type = String.valueOf(rule.getOrDefault("ruleType", "")).toUpperCase(Locale.ROOT);
            String outcome = String.valueOf(rule.getOrDefault("outcome", "")).toUpperCase(Locale.ROOT);
            if ("FAIL".equals(outcome) && "KNOCKOUT".equals(type)) {
                knockoutFail = true;
            }
            if ("FAIL".equals(outcome) && "HARD".equals(type)) {
                hardFail = true;
            }
        }

        if (knockoutFail) {
            return mapOr(mapping, "KNOCKOUT_FAIL", "DECLINE");
        }
        if (hardFail || "FAIL".equals(overall)) {
            return mapOr(mapping, "HARD_FAIL", mapOr(mapping, "FAIL", "DECLINE"));
        }
        if ("REFER".equals(overall)) {
            return mapOr(mapping, "REFER", "REFER");
        }
        if ("DATA_INSUFFICIENT".equals(overall) || "DI".equals(overall)) {
            return mapOr(mapping, "DATA_INSUFFICIENT", "DATA_INSUFFICIENT");
        }
        if ("PASS".equals(overall) || "CONTINUE".equals(overall)) {
            String m = mapOr(mapping, "PASS", "CONTINUE");
            return "CONTINUE".equals(m) ? "CONTINUE" : m;
        }
        return mapOr(mapping, overall, "REFER");
    }

    private String mapOr(Map<String, Object> mapping, String key, String def) {
        Object v = mapping.get(key);
        return v == null ? def : String.valueOf(v).toUpperCase(Locale.ROOT);
    }

    private boolean isTerminal(String mapped) {
        return "DECLINE".equals(mapped) || "REFER".equals(mapped) || "DATA_INSUFFICIENT".equals(mapped);
    }

    private RecommendationOutcome resolveStructuringOutcome(
            LimitMethodEngine.LimitResult limits,
            ConditionEngine.ConditionResult conditions,
            CollateralEngine.CollateralResult collateral,
            PricingEngine.PricingResult pricing,
            Map<String, Object> content) {
        if (limits.amountOutcome() == RecommendationOutcome.DATA_INSUFFICIENT) {
            return RecommendationOutcome.DATA_INSUFFICIENT;
        }
        if (pricing.evidenceWeaknessCausesRefer()
                && pricing.limitations().contains("MISSING_RISK_GRADE")
                && Boolean.TRUE.equals(DecisionValueHelper.asMap(content.get("pricingStrategy"))
                .get("evidenceWeaknessCausesRefer"))) {
            // Prefer REFER for evidence weakness when configured — unless amount already counter-offer with conditions
        }
        boolean hasConditions = conditions != null && !conditions.conditions().isEmpty();
        if (limits.amountOutcome() == RecommendationOutcome.COUNTER_OFFER) {
            return RecommendationOutcome.COUNTER_OFFER;
        }
        if (hasConditions || (collateral != null && !collateral.adequate() && collateral.required())) {
            return RecommendationOutcome.APPROVE_WITH_CONDITIONS;
        }
        return RecommendationOutcome.APPROVE;
    }

    private Map<String, Object> eligibilityDimension(DecisionRuntimeInput input, String mapped) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyOverallOutcome", input.policyOverallOutcome());
        m.put("mapped", mapped);
        m.put("status", "CONTINUE".equals(mapped) || "APPROVE".equals(mapped) ? "ELIGIBLE" : mapped);
        return m;
    }

    private Map<String, Object> riskGradeDimension(DecisionRuntimeInput input) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("grade", input.scoreResult().get("grade"));
        m.put("score", input.scoreResult().get("score"));
        m.put("source", "POLICY_SCORECARD");
        return m;
    }

    private void applyBankingBureauHelpers(DecisionRuntimeInput input, Map<String, Object> content,
                                           Map<String, Object> dimensions, List<Object> limitations,
                                           List<String> reasonCodes) {
        String overall = input.policyOverallOutcome() == null ? "" : input.policyOverallOutcome().toUpperCase(Locale.ROOT);
        boolean bankingStage = false;
        boolean bureauFail = false;
        for (Map<String, Object> rule : input.policyRuleResults()) {
            String stage = String.valueOf(rule.getOrDefault("stageCode", rule.getOrDefault("stage", "")));
            String outcome = String.valueOf(rule.getOrDefault("outcome", ""));
            if ("BANKING".equalsIgnoreCase(stage) && "PASS".equalsIgnoreCase(outcome)) {
                bankingStage = true;
            }
            if ("BUREAU".equalsIgnoreCase(stage) && "FAIL".equalsIgnoreCase(outcome)) {
                bureauFail = true;
            }
        }
        if (bankingStage || "PASS".equals(overall)) {
            Map<String, Object> elig = new LinkedHashMap<>(DecisionValueHelper.asMap(dimensions.get("Eligibility")));
            if (bankingStage) {
                elig.put("bankingBre", "ELIGIBLE");
                reasonCodes.add("BANKING_BRE_PASS");
            }
            dimensions.put("Eligibility", elig);
            boolean validationFixture = Boolean.TRUE.equals(content.get("validationFixture"));
            Map<String, Object> limitParams = DecisionValueHelper.asMap(
                    DecisionValueHelper.asMap(content.get("limitStrategy")).get("params"));
            if (!validationFixture && limitParams.get("policyCap") == null
                    && input.policyParameters().get("POLICY_CAP") == null
                    && input.requestedAmount() == null) {
                limitations.add("POLICY_PARAMETER_REQUIRED");
                reasonCodes.add("POLICY_PARAMETER_REQUIRED");
            }
        }
        if (bureauFail) {
            reasonCodes.add("BUREAU_BRE_FAIL");
        }
    }

    private Map<String, Object> buildReasonGraph(RecommendationOutcome outcome,
                                                 LimitMethodEngine.LimitResult limits,
                                                 TenureEngine.TenureResult tenure,
                                                 PricingEngine.PricingResult pricing,
                                                 ConditionEngine.ConditionResult conditions) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("recommendation", outcome.name());
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (limits != null) {
            nodes.add(Map.of(
                    "dimension", "Limit",
                    "selectedMethod", limits.selectedMethod() == null ? "" : limits.selectedMethod(),
                    "eligibleAmount", limits.selectedEligibleAmount() == null ? "" : limits.selectedEligibleAmount(),
                    "recommendedAmount", limits.recommendedAmount() == null ? "" : limits.recommendedAmount()));
        }
        if (tenure != null) {
            nodes.add(Map.of("dimension", "Tenure", "recommended", tenure.recommendedTenure(), "reason", tenure.reason()));
        }
        if (pricing != null) {
            nodes.add(Map.of("dimension", "Pricing", "finalRate", pricing.finalRate()));
        }
        if (conditions != null) {
            nodes.add(Map.of("dimension", "Conditions", "count", conditions.conditions().size()));
        }
        graph.put("dimensions", nodes);
        return graph;
    }
}
