package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.underwriting.CanonicalScorecardValueResolver;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine.FactorDataState;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine.FactorInput;
import com.los.core.service.underwriting.PolicyWeightedScorecardEngine.ScoreResult;
import com.los.core.service.underwriting.ScorecardCanonicalFactorMapper;
import com.los.core.service.underwriting.ScorecardExclusiveBandModel;
import com.los.core.service.underwriting.ScorecardSafetyScoring;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Executes the frozen policy-linked scorecard using CPES values.
 * Does not call the legacy scorecard product-matcher evaluate path (no latest ACTIVE lookup).
 *
 * <p>POLICY_WEIGHTED_V2 PRESENT factors use {@link PolicyWeightedScorecardEngine}.
 * Configured executable factors are never silently skipped.
 */
@Component
@RequiredArgsConstructor
public class CanonicalShadowScorecardExecutor {

    static final String CONDITION_PRESENT = "PRESENT";
    static final String CONDITION_ABSENT = "ABSENT";
    static final String KIND_PRESENT = "PRESENT";
    static final String KIND_ABSENT = "ABSENT";
    static final String KIND_EXCLUSIVE = "EXCLUSIVE_RANGES";
    static final String KIND_BOOLEAN_OR_MATCH = "BOOLEAN_OR_MATCH";

    private final UnderwritingScorecardRepository scorecardRepository;
    private final CanonicalParameterExecutionService cpes;

    public Map<String, Object> execute(CanonicalApplicationConfiguration freeze, EvaluationContext spine) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engine", "CanonicalShadowScorecardExecutor");
        out.put("productMatcherUsed", false);
        out.put("scorecardPolicyEngineEvaluateUsed", false);
        if (freeze.scorecardExplicitlyAbsent() || freeze.scorecardId() == null) {
            out.put("available", false);
            out.put("scorecardExplicitlyAbsent", true);
            out.put("reason", "SCORECARD_EXPLICITLY_ABSENT");
            out.put("bandOutcome", FinalUnderwritingDecision.FinalOutcome.REFER.name());
            return out;
        }
        Optional<UnderwritingScorecard> opt = scorecardRepository.findById(freeze.scorecardId());
        if (opt.isEmpty()) {
            out.put("available", false);
            out.put("executable", false);
            out.put("reason", CanonicalShadowFailureCode.SCORECARD_NOT_RESOLVABLE.name());
            out.put("bandOutcome", FinalUnderwritingDecision.FinalOutcome.ERROR.name());
            return out;
        }
        UnderwritingScorecard card = opt.get();
        if (freeze.scorecardVersion() != null && card.getVersion() != freeze.scorecardVersion()) {
            out.put("available", false);
            out.put("executable", false);
            out.put("reason", CanonicalShadowFailureCode.SCORECARD_NOT_RESOLVABLE.name());
            out.put("frozenVersion", freeze.scorecardVersion());
            out.put("loadedVersion", card.getVersion());
            out.put("bandOutcome", FinalUnderwritingDecision.FinalOutcome.ERROR.name());
            return out;
        }
        out.put("scorecardId", card.getId().toString());
        out.put("scorecardVersion", card.getVersion());
        out.put("scorecardStatus", card.getStatus());
        out.put("scorecardName", card.getName());
        out.put("draftExecutedForShadow", !"ACTIVE".equalsIgnoreCase(card.getStatus()));

        Map<String, Object> scj = card.getScorecardJson() == null ? Map.of() : card.getScorecardJson();
        List<Map<String, Object>> rowMaps = rowMaps(scj);
        boolean weighted = isWeighted(card, scj);
        out.put("scoringMode", weighted ? PolicyWeightedScorecardEngine.MODE : "LEGACY_POINTS_V1");

        ScorecardExclusiveBandModel.Model model = ScorecardExclusiveBandModel.fromRows(rowMaps);
        if (!model.safe()) {
            out.put("available", true);
            out.put("executable", false);
            out.put("reason", CanonicalShadowFailureCode.SCORECARD_NOT_EXECUTABLE.name());
            out.put("problems", model.problems());
            out.put("configuredExecutableFactorSkippedCount", 0);
            out.put("bandOutcome", FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT.name());
            return out;
        }

        FactorPlan plan = planFactors(rowMaps, model, weighted, card);
        if (!plan.unsupported().isEmpty()) {
            out.put("available", true);
            out.put("executable", false);
            out.put("reason", CanonicalShadowFailureCode.SCORECARD_NOT_EXECUTABLE.name());
            out.put("problems", plan.unsupported());
            out.put("configuredExecutableFactorSkippedCount", plan.unsupported().size());
            out.put("bandOutcome", FinalUnderwritingDecision.FinalOutcome.ERROR.name());
            return out;
        }
        if (plan.factors().isEmpty()) {
            out.put("available", true);
            out.put("executable", false);
            out.put("reason", CanonicalShadowFailureCode.SCORECARD_NOT_EXECUTABLE.name());
            out.put("problems", List.of("NO_CONFIGURED_FACTORS"));
            out.put("configuredExecutableFactorSkippedCount", 0);
            out.put("bandOutcome", FinalUnderwritingDecision.FinalOutcome.ERROR.name());
            return out;
        }

        if (weighted) {
            return executeWeighted(out, card, freeze, spine, plan);
        }
        return executeLegacyPoints(out, card, freeze, spine, plan);
    }

    private Map<String, Object> executeWeighted(
            Map<String, Object> out,
            UnderwritingScorecard card,
            CanonicalApplicationConfiguration freeze,
            EvaluationContext spine,
            FactorPlan plan) {
        List<FactorInput> inputs = new ArrayList<>();
        List<Map<String, Object>> paramResults = new ArrayList<>();
        List<String> inputIds = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int skipped = 0;

        for (PlannedFactor factor : plan.factors()) {
            ResolvedParameter resolved = resolveParameter(factor.parameter(), spine);
            if (resolved.canonicalId() != null) {
                inputIds.add(resolved.canonicalId());
            }
            ScoredFactor scored = scoreFactor(factor, resolved);
            inputs.add(new FactorInput(
                    scored.canonicalId(),
                    scored.rawWeight(),
                    scored.required(),
                    scored.dataState(),
                    scored.earned(),
                    scored.max()));
            Map<String, Object> row = evidenceRow(factor, resolved, scored);
            if (!scored.valueAvailable() && scored.required()) {
                missing.add(factor.parameter());
            }
            paramResults.add(row);
        }

        ScoreResult result = PolicyWeightedScorecardEngine.score(inputs);
        Map<String, Object> thresholds = card.getThresholdsJson() == null ? Map.of() : card.getThresholdsJson();
        int approveMin = intOr(thresholds.get("approveMinPercent"), 70);
        int manualMin = intOr(thresholds.get("manualMinPercent"), 40);

        FinalUnderwritingDecision.FinalOutcome band;
        int percent;
        if (!result.valid()) {
            band = FinalUnderwritingDecision.FinalOutcome.ERROR;
            percent = 0;
            out.put("executable", false);
            out.put("reason", CanonicalShadowFailureCode.SCORECARD_NOT_EXECUTABLE.name());
            out.put("problems", result.errors());
        } else if (result.dataInsufficient()) {
            band = FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
            percent = 0;
            out.put("executable", true);
        } else {
            BigDecimal weighted = result.weightedScore() == null ? BigDecimal.ZERO : result.weightedScore();
            percent = weighted.setScale(0, RoundingMode.HALF_UP).intValue();
            band = bandForPercent(percent, approveMin, manualMin, false);
            out.put("executable", true);
        }

        out.put("available", true);
        out.put("numericalScore", percent);
        out.put("earned", percent);
        out.put("maxPoints", 100);
        out.put("weightedScore", result.weightedScore());
        out.put("band", band.name());
        out.put("bandOutcome", band.name());
        out.put("outcome", band.name());
        out.put("inputParameterIds", inputIds);
        out.put("inputValues", paramResults);
        out.put("missingInputs", missing);
        out.put("parameterResults", paramResults);
        out.put("factorEvidence", result.factorEvidence());
        out.put("hardRuleResults", List.of());
        out.put("hardRulesDeferredToPolicy", true);
        out.put("valueProvenance", ScorecardValueProvenance.REAL_PROVIDER);
        out.put("configuredFactorCount", plan.factors().size());
        out.put("configuredExecutableFactorSkippedCount", skipped);
        out.put("scorecardZeroMaxWithConfiguredFactors", 0);
        out.put("frozenScorecardId", freeze.scorecardId().toString());
        return out;
    }

    private Map<String, Object> executeLegacyPoints(
            Map<String, Object> out,
            UnderwritingScorecard card,
            CanonicalApplicationConfiguration freeze,
            EvaluationContext spine,
            FactorPlan plan) {
        int earned = 0;
        int maxPoints = 0;
        boolean dataInsufficient = false;
        List<Map<String, Object>> paramResults = new ArrayList<>();
        List<String> inputIds = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int skipped = 0;

        for (PlannedFactor factor : plan.factors()) {
            ResolvedParameter resolved = resolveParameter(factor.parameter(), spine);
            if (resolved.canonicalId() != null) {
                inputIds.add(resolved.canonicalId());
            }
            ScoredFactor scored = scoreFactor(factor, resolved);
            Map<String, Object> row = evidenceRow(factor, resolved, scored);
            if (scored.dataState() == FactorDataState.NOT_APPLICABLE) {
                paramResults.add(row);
                continue;
            }
            if (scored.required() && (scored.dataState() == FactorDataState.MISSING
                    || scored.dataState() == FactorDataState.DATA_INSUFFICIENT
                    || scored.dataState() == FactorDataState.INVALID)) {
                dataInsufficient = true;
                missing.add(factor.parameter());
                paramResults.add(row);
                continue;
            }
            int factorMax = scored.max() == null ? 0 : scored.max().intValue();
            int factorEarned = scored.earned() == null ? 0 : scored.earned().intValue();
            maxPoints += factorMax;
            earned += factorEarned;
            paramResults.add(row);
        }

        int percent = maxPoints <= 0 ? 0
                : BigDecimal.valueOf(earned * 100L)
                .divide(BigDecimal.valueOf(maxPoints), 0, RoundingMode.HALF_UP)
                .intValue();
        Map<String, Object> thresholds = card.getThresholdsJson() == null ? Map.of() : card.getThresholdsJson();
        int approveMin = intOr(thresholds.get("approveMinPercent"), 70);
        int manualMin = intOr(thresholds.get("manualMinPercent"), 40);
        FinalUnderwritingDecision.FinalOutcome band = bandForPercent(percent, approveMin, manualMin, dataInsufficient);

        out.put("available", true);
        out.put("executable", true);
        out.put("numericalScore", percent);
        out.put("earned", earned);
        out.put("maxPoints", maxPoints);
        out.put("band", band.name());
        out.put("bandOutcome", band.name());
        out.put("outcome", band.name());
        out.put("inputParameterIds", inputIds);
        out.put("inputValues", paramResults);
        out.put("missingInputs", missing);
        out.put("parameterResults", paramResults);
        out.put("hardRuleResults", List.of());
        out.put("hardRulesDeferredToPolicy", true);
        out.put("valueProvenance", ScorecardValueProvenance.REAL_PROVIDER);
        out.put("configuredFactorCount", plan.factors().size());
        out.put("configuredExecutableFactorSkippedCount", skipped);
        out.put("scorecardZeroMaxWithConfiguredFactors",
                (!plan.factors().isEmpty() && maxPoints <= 0 && !dataInsufficient) ? 1 : 0);
        out.put("frozenScorecardId", freeze.scorecardId().toString());
        return out;
    }

    private ScoredFactor scoreFactor(PlannedFactor factor, ResolvedParameter resolved) {
        String canonicalId = resolved.canonicalId() != null ? resolved.canonicalId() : factor.parameter();
        boolean required = factor.required();
        BigDecimal weight = factor.rawWeight();
        boolean available = resolved.available();
        FactorDataState fromStatus = dataStateFromExecution(resolved.status(), available);

        if (KIND_PRESENT.equals(factor.kind())) {
            if (available) {
                BigDecimal max = factor.bandPointsMax();
                BigDecimal earned = factor.bandPointsEarned();
                return new ScoredFactor(canonicalId, weight, required, FactorDataState.PRESENT,
                        earned, max, true, factor.condition(), true);
            }
            return missingScored(canonicalId, weight, required, factor, fromStatus);
        }
        if (KIND_ABSENT.equals(factor.kind())) {
            // Configured ABSENT: award configured points when the value is not present.
            if (!available) {
                return new ScoredFactor(canonicalId, weight, false, FactorDataState.PRESENT,
                        factor.bandPointsEarned(), factor.bandPointsMax(), false, factor.condition(), true);
            }
            return new ScoredFactor(canonicalId, weight, false, FactorDataState.PRESENT,
                    BigDecimal.ZERO, factor.bandPointsMax(), true, factor.condition(), false);
        }
        if (KIND_EXCLUSIVE.equals(factor.kind())) {
            if (!available) {
                return missingScored(canonicalId, weight, required, factor, fromStatus);
            }
            ScorecardExclusiveBandModel.ExclusiveBand matched =
                    ScorecardExclusiveBandModel.match(factor.bands(), resolved.numeric(), resolved.stringValue());
            BigDecimal max = BigDecimal.valueOf(factor.bands().maxPoints());
            BigDecimal earned = matched != null ? BigDecimal.valueOf(matched.score()) : BigDecimal.ZERO;
            return new ScoredFactor(canonicalId, weight, required, FactorDataState.PRESENT,
                    earned, max, true, matched == null ? factor.condition() : matched.originalCondition(),
                    matched != null);
        }
        // BOOLEAN / EQ / NE / MATCH_OPTION / text — existing condition contract
        if (!available) {
            return missingScored(canonicalId, weight, required, factor, fromStatus);
        }
        boolean matched = matchSingle(factor, resolved);
        BigDecimal max = factor.bandPointsMax();
        BigDecimal earned = matched ? factor.bandPointsEarned() : BigDecimal.ZERO;
        return new ScoredFactor(canonicalId, weight, required, FactorDataState.PRESENT,
                earned, max, true, factor.condition(), matched);
    }

    private static ScoredFactor missingScored(
            String canonicalId,
            BigDecimal weight,
            boolean required,
            PlannedFactor factor,
            FactorDataState fromStatus) {
        String policy = factor.missingPolicy();
        if (ScorecardSafetyScoring.MISSING_OPTIONAL_SKIP.equals(policy)) {
            return new ScoredFactor(canonicalId, weight, false, FactorDataState.NOT_APPLICABLE,
                    BigDecimal.ZERO, BigDecimal.ZERO, false, factor.condition(), false);
        }
        if (ScorecardSafetyScoring.MISSING_OPTIONAL_DEPRESS.equals(policy)) {
            return new ScoredFactor(canonicalId, weight, false, FactorDataState.PRESENT,
                    BigDecimal.ZERO, factor.bandPointsMax(), false, factor.condition(), false);
        }
        FactorDataState state = fromStatus == FactorDataState.INVALID
                ? FactorDataState.INVALID
                : (fromStatus == FactorDataState.DATA_INSUFFICIENT
                ? FactorDataState.DATA_INSUFFICIENT
                : FactorDataState.MISSING);
        return new ScoredFactor(canonicalId, weight, required, state,
                BigDecimal.ZERO, factor.bandPointsMax(), false, factor.condition(), false);
    }

    private static boolean matchSingle(PlannedFactor factor, ResolvedParameter resolved) {
        String cond = factor.condition();
        if ("MATCH_OPTION".equalsIgnoreCase(cond)) {
            return resolved.available();
        }
        if (resolved.numeric() != null
                && com.los.core.service.underwriting.ScorecardPolicyEngineAccess.conditionMatches(
                        cond, resolved.numeric())) {
            return true;
        }
        return resolved.stringValue() != null
                && com.los.core.service.underwriting.ScorecardPolicyEngineAccess.stringConditionMatches(
                        cond, resolved.stringValue());
    }

    private ResolvedParameter resolveParameter(String parameter, EvaluationContext spine) {
        ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(parameter);
        CanonicalScorecardValueResolver.ResolveOutcome resolved;
        String canonical;
        if (bind.canonicalParameterId() != null
                && (ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                || ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus()))) {
            canonical = bind.canonicalParameterId();
            resolved = CanonicalScorecardValueResolver.resolveCanonical(canonical, spine, cpes);
        } else if (parameter != null && parameter.contains(".")) {
            canonical = parameter;
            resolved = CanonicalScorecardValueResolver.resolveCanonical(parameter, spine, cpes);
        } else {
            return new ResolvedParameter(parameter, bind.canonicalParameterId(), bind.mappingStatus(),
                    null, false, null, null, null);
        }
        return new ResolvedParameter(
                parameter,
                canonical,
                bind.mappingStatus(),
                resolved,
                resolved != null && resolved.valueAvailable(),
                resolved == null ? null : resolved.numericValue(),
                resolved == null ? null : resolved.stringValue(),
                resolved == null ? null : resolved.status());
    }

    private static Map<String, Object> evidenceRow(
            PlannedFactor factor, ResolvedParameter resolved, ScoredFactor scored) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("parameter", factor.parameter());
        row.put("canonicalParameterId", scored.canonicalId());
        row.put("mappingStatus", resolved.mappingStatus());
        row.put("condition", factor.condition());
        row.put("conditionKind", factor.kind());
        row.put("valueAvailable", scored.valueAvailable());
        row.put("canonicalStatus", resolved.status() == null ? "NOT_MAPPED" : String.valueOf(resolved.status()));
        row.put("calculationAuthority", CanonicalScorecardValueResolver.AUTHORITY);
        row.put("valueUsed", resolved.numeric() != null
                ? resolved.numeric().toPlainString()
                : resolved.stringValue());
        row.put("dataState", scored.dataState().name());
        row.put("missingDataPolicy", factor.missingPolicy());
        row.put("required", scored.required());
        row.put("rawWeight", scored.rawWeight());
        row.put("matched", scored.matched());
        row.put("pointsEarned", scored.earned());
        row.put("maxScore", scored.max());
        if (resolved.outcome() != null) {
            row.put("execution", resolved.outcome().toTraceMap());
        }
        return row;
    }

    static FactorPlan planFactors(
            List<Map<String, Object>> rowMaps,
            ScorecardExclusiveBandModel.Model model,
            boolean weighted,
            UnderwritingScorecard card) {
        Map<String, Object> safety = card.getSafetyJson() == null ? Map.of() : card.getSafetyJson();
        @SuppressWarnings("unchecked")
        Map<String, Object> factorPolicies = safety.get("factorPolicies") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        List<PlannedFactor> factors = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        Set<String> consumed = new LinkedHashSet<>();

        for (ScorecardExclusiveBandModel.FactorBands fb : model.factors()) {
            if (ScorecardExclusiveBandModel.MODE_EXCLUSIVE.equals(fb.mode())) {
                Map<String, Object> sample = firstRow(rowMaps, fb.parameter());
                factors.add(new PlannedFactor(
                        fb.parameter(),
                        KIND_EXCLUSIVE,
                        conditionOf(sample),
                        missingPolicyOf(sample, fb.parameter(), factorPolicies, weighted),
                        weightOf(sample),
                        BigDecimal.valueOf(fb.maxPoints()),
                        BigDecimal.valueOf(fb.maxPoints()),
                        requiredOf(sample, fb.parameter(), factorPolicies, weighted),
                        fb));
                consumed.add(fb.parameter());
            }
        }
        for (Map<String, Object> row : rowMaps) {
            String parameter = str(row.get("parameter"));
            if (parameter == null || consumed.contains(parameter)) {
                continue;
            }
            consumed.add(parameter);
            String cond = str(row.get("condition"));
            String kind = classifyCondition(cond, weighted);
            if (kind == null) {
                unsupported.add(parameter + ":UNSUPPORTED_CONDITION:" + cond);
                continue;
            }
            BigDecimal points = BigDecimal.valueOf(intOr(row.get("score"), weighted ? 100 : 0));
            BigDecimal max = weighted ? BigDecimal.valueOf(100) : points;
            factors.add(new PlannedFactor(
                    parameter,
                    kind,
                    cond == null || cond.isBlank() ? CONDITION_PRESENT : cond,
                    missingPolicyOf(row, parameter, factorPolicies, weighted),
                    weightOf(row),
                    points,
                    max,
                    requiredOf(row, parameter, factorPolicies, weighted),
                    null));
        }
        return new FactorPlan(factors, unsupported);
    }

    static String classifyCondition(String cond, boolean weighted) {
        if (cond == null || cond.isBlank()) {
            return weighted ? KIND_PRESENT : null;
        }
        String c = cond.trim().toUpperCase(Locale.ROOT);
        if (CONDITION_PRESENT.equals(c)) {
            return KIND_PRESENT;
        }
        if (CONDITION_ABSENT.equals(c)) {
            return KIND_ABSENT;
        }
        if ("MATCH_OPTION".equals(c)) {
            return KIND_BOOLEAN_OR_MATCH;
        }
        int colon = c.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String op = c.substring(0, colon).trim();
        if (Set.of("GTE", "GT", "LTE", "LT", "BETWEEN", "EQ", "NE", "CONTAINS", "NOT_CONTAINS").contains(op)) {
            return KIND_BOOLEAN_OR_MATCH;
        }
        return null;
    }

    private static boolean isWeighted(UnderwritingScorecard card, Map<String, Object> scj) {
        if (PolicyWeightedScorecardEngine.MODE.equalsIgnoreCase(card.getScoringMode())) {
            return true;
        }
        return PolicyWeightedScorecardEngine.MODE.equalsIgnoreCase(str(scj.get("mode")));
    }

    private static FactorDataState dataStateFromExecution(ExecutionStatus status, boolean available) {
        if (available) {
            return FactorDataState.PRESENT;
        }
        if (status == ExecutionStatus.ERROR || status == ExecutionStatus.NOT_EXECUTABLE) {
            return FactorDataState.INVALID;
        }
        if (status == ExecutionStatus.DATA_NOT_AVAILABLE
                || status == ExecutionStatus.DEPENDENCY_NOT_AVAILABLE
                || status == ExecutionStatus.CALCULATION_NOT_DEFINED
                || status == ExecutionStatus.INPUT_REQUIRED) {
            return FactorDataState.DATA_INSUFFICIENT;
        }
        return FactorDataState.MISSING;
    }

    private static FinalUnderwritingDecision.FinalOutcome bandForPercent(
            int percent, int approveMin, int manualMin, boolean dataInsufficient) {
        if (dataInsufficient) {
            return FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
        }
        if (percent >= approveMin) {
            return FinalUnderwritingDecision.FinalOutcome.APPROVE;
        }
        if (percent >= manualMin) {
            return FinalUnderwritingDecision.FinalOutcome.REFER;
        }
        return FinalUnderwritingDecision.FinalOutcome.REJECT;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowMaps(Map<String, Object> scj) {
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        Object rows = scj.get("rows");
        if (rows instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    rowMaps.add((Map<String, Object>) m);
                }
            }
        }
        return rowMaps;
    }

    private static Map<String, Object> firstRow(List<Map<String, Object>> rows, String parameter) {
        for (Map<String, Object> row : rows) {
            if (parameter.equals(str(row.get("parameter")))) {
                return row;
            }
        }
        return Map.of();
    }

    private static String conditionOf(Map<String, Object> row) {
        String c = str(row.get("condition"));
        return c == null || c.isBlank() ? CONDITION_PRESENT : c;
    }

    private static BigDecimal weightOf(Map<String, Object> row) {
        Object w = row.get("weight");
        if (w instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ONE;
    }

    private static String missingPolicyOf(
            Map<String, Object> row, String parameter, Map<String, Object> factorPolicies, boolean weighted) {
        String fromRow = str(row.get("missingData"));
        if (isMissingPolicy(fromRow)) {
            return fromRow.trim().toUpperCase(Locale.ROOT);
        }
        Object pol = factorPolicies.get(parameter);
        if (pol instanceof Map<?, ?> m && m.get("missingData") != null) {
            String configured = String.valueOf(m.get("missingData")).trim().toUpperCase(Locale.ROOT);
            if (isMissingPolicy(configured)) {
                return configured;
            }
        }
        return weighted ? ScorecardSafetyScoring.MISSING_REQUIRED : ScorecardSafetyScoring.MISSING_OPTIONAL_DEPRESS;
    }

    private static boolean requiredOf(
            Map<String, Object> row, String parameter, Map<String, Object> factorPolicies, boolean weighted) {
        if (row.get("required") instanceof Boolean b) {
            return b;
        }
        String policy = missingPolicyOf(row, parameter, factorPolicies, weighted);
        return ScorecardSafetyScoring.MISSING_REQUIRED.equals(policy);
    }

    private static boolean isMissingPolicy(String v) {
        if (v == null || v.isBlank()) {
            return false;
        }
        String u = v.trim().toUpperCase(Locale.ROOT);
        return ScorecardSafetyScoring.MISSING_REQUIRED.equals(u)
                || ScorecardSafetyScoring.MISSING_OPTIONAL_SKIP.equals(u)
                || ScorecardSafetyScoring.MISSING_OPTIONAL_DEPRESS.equals(u);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static int intOr(Object o, int d) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return d;
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return d;
        }
    }

    record PlannedFactor(
            String parameter,
            String kind,
            String condition,
            String missingPolicy,
            BigDecimal rawWeight,
            BigDecimal bandPointsEarned,
            BigDecimal bandPointsMax,
            boolean required,
            ScorecardExclusiveBandModel.FactorBands bands) {}

    record FactorPlan(List<PlannedFactor> factors, List<String> unsupported) {}

    record ResolvedParameter(
            String parameter,
            String canonicalId,
            String mappingStatus,
            CanonicalScorecardValueResolver.ResolveOutcome outcome,
            boolean available,
            BigDecimal numeric,
            String stringValue,
            ExecutionStatus status) {}

    record ScoredFactor(
            String canonicalId,
            BigDecimal rawWeight,
            boolean required,
            FactorDataState dataState,
            BigDecimal earned,
            BigDecimal max,
            boolean valueAvailable,
            String condition,
            boolean matched) {}
}
