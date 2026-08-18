package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.underwriting.CanonicalScorecardValueResolver;
import com.los.core.service.underwriting.ScorecardCanonicalFactorMapper;
import com.los.core.service.underwriting.ScorecardExclusiveBandModel;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes the frozen policy-linked scorecard using CPES values.
 * Does not call the legacy scorecard product-matcher evaluate path (no latest ACTIVE lookup).
 */
@Component
@RequiredArgsConstructor
public class CanonicalShadowScorecardExecutor {

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
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        Object rows = scj.get("rows");
        if (rows instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) m;
                    rowMaps.add(row);
                }
            }
        }
        ScorecardExclusiveBandModel.Model model = ScorecardExclusiveBandModel.fromRows(rowMaps);
        if (!model.safe()) {
            out.put("available", true);
            out.put("executable", false);
            out.put("reason", CanonicalShadowFailureCode.SCORECARD_NOT_EXECUTABLE.name());
            out.put("problems", model.problems());
            out.put("bandOutcome", FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT.name());
            return out;
        }

        int earned = 0;
        int maxPoints = 0;
        boolean dataInsufficient = false;
        List<Map<String, Object>> paramResults = new ArrayList<>();
        List<String> inputIds = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (ScorecardExclusiveBandModel.FactorBands factor : model.factors()) {
            if (!ScorecardExclusiveBandModel.MODE_EXCLUSIVE.equals(factor.mode())) {
                continue;
            }
            String parameter = factor.parameter();
            ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(parameter);
            CanonicalScorecardValueResolver.ResolveOutcome resolved;
            if (bind.canonicalParameterId() != null
                    && (ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                    || ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus()))) {
                resolved = CanonicalScorecardValueResolver.resolveCanonical(
                        bind.canonicalParameterId(), spine, cpes);
                inputIds.add(bind.canonicalParameterId());
            } else if (parameter != null && parameter.contains(".")) {
                resolved = CanonicalScorecardValueResolver.resolveCanonical(parameter, spine, cpes);
                inputIds.add(parameter);
            } else {
                resolved = null;
                missing.add(parameter + ":NO_CANONICAL_MAPPING");
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("parameter", parameter);
            row.put("canonicalParameterId", bind.canonicalParameterId());
            row.put("mappingStatus", bind.mappingStatus());
            BigDecimal value = resolved == null ? null : resolved.numericValue();
            String stringValue = resolved == null ? null : resolved.stringValue();
            boolean available = resolved != null && resolved.valueAvailable();
            row.put("valueAvailable", available);
            row.put("canonicalStatus", resolved == null ? "NOT_MAPPED" : String.valueOf(resolved.status()));
            row.put("calculationAuthority", CanonicalScorecardValueResolver.AUTHORITY);
            row.put("valueUsed", value != null ? value.toPlainString() : stringValue);
            if (resolved != null) {
                row.put("execution", resolved.toTraceMap());
            }
            if (!available) {
                dataInsufficient = true;
                missing.add(parameter);
                row.put("matched", false);
                row.put("pointsEarned", 0);
                row.put("maxScore", factor.maxPoints());
                paramResults.add(row);
                continue;
            }
            ScorecardExclusiveBandModel.ExclusiveBand matched =
                    ScorecardExclusiveBandModel.match(factor, value, stringValue);
            maxPoints += factor.maxPoints();
            int add = matched != null ? matched.score() : 0;
            earned += add;
            row.put("matched", matched != null);
            row.put("pointsEarned", add);
            row.put("maxScore", factor.maxPoints());
            row.put("matchedBand", matched == null ? null : matched.originalCondition());
            paramResults.add(row);
        }

        int percent = maxPoints <= 0 ? 0
                : BigDecimal.valueOf(earned * 100L)
                .divide(BigDecimal.valueOf(maxPoints), 0, RoundingMode.HALF_UP)
                .intValue();
        Map<String, Object> thresholds = card.getThresholdsJson() == null ? Map.of() : card.getThresholdsJson();
        int approveMin = intOr(thresholds.get("approveMinPercent"), 70);
        int manualMin = intOr(thresholds.get("manualMinPercent"), 40);
        FinalUnderwritingDecision.FinalOutcome band;
        if (dataInsufficient) {
            band = FinalUnderwritingDecision.FinalOutcome.DATA_INSUFFICIENT;
        } else if (percent >= approveMin) {
            band = FinalUnderwritingDecision.FinalOutcome.APPROVE;
        } else if (percent >= manualMin) {
            band = FinalUnderwritingDecision.FinalOutcome.REFER;
        } else {
            band = FinalUnderwritingDecision.FinalOutcome.REJECT;
        }

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
        return out;
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
}
