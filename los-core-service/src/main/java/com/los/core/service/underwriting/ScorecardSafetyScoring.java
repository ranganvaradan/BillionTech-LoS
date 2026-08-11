package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.service.credit.EffectiveUnderwritingContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SCORECARD-SAFETY-FOUNDATION-1 — exclusive-band scoring + provenance + missing-data policy.
 */
public final class ScorecardSafetyScoring {

    public static final String MISSING_REQUIRED = "REQUIRED";
    public static final String MISSING_OPTIONAL_SKIP = "OPTIONAL_SKIP";
    public static final String MISSING_OPTIONAL_DEPRESS = "OPTIONAL_DEPRESS";

    private ScorecardSafetyScoring() {}

    public record ScoreOutcome(
            int earned,
            int maxPoints,
            int normalizedPercent,
            String policyDecision,
            String creditDecision,
            List<Map<String, Object>> parameterResults,
            Map<String, Object> evidence,
            boolean dataInsufficient,
            boolean nonAuthoritativeBlocked,
            boolean unsafeBands,
            List<String> reasons) {}

    @SuppressWarnings("unchecked")
    public static ScoreOutcome score(
            UnderwritingScorecard card,
            LoanApplication app,
            EffectiveUnderwritingContext ctx,
            boolean blockNonAuthoritative,
            boolean allowNonProductionDemoScoring) {

        Map<String, Object> scj = card.getScorecardJson() != null ? card.getScorecardJson() : Map.of();
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        Object rows = scj.get("rows");
        if (rows instanceof List<?> rlist) {
            for (Object o : rlist) {
                if (o instanceof Map) rowMaps.add((Map<String, Object>) o);
            }
        }
        Map<String, Map<String, Object>> parameterDefs = parseParameterDefs(scj);
        Map<String, Object> safety = card.getSafetyJson() != null ? card.getSafetyJson() : Map.of();
        Map<String, Object> factorPolicies = safety.get("factorPolicies") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        Set<String> requiredFromHard = hardRuleParameters(card);
        ScorecardExclusiveBandModel.Model model = ScorecardExclusiveBandModel.fromRows(rowMaps);
        if (!model.safe()) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("bandMode", ScorecardExclusiveBandModel.MODE_AMBIGUOUS);
            evidence.put("problems", model.problems());
            return new ScoreOutcome(
                    0, 0, 50, "MANUAL_REVIEW", "MANUAL_REVIEW", List.of(), evidence, true, false, true,
                    List.of("Scorecard band configuration is ambiguous/unsafe: " + String.join("; ", model.problems())));
        }

        int earned = 0;
        int maxPoints = 0;
        List<Map<String, Object>> paramResults = new ArrayList<>();
        boolean dataInsufficient = false;
        boolean nonAuthBlocked = false;
        List<String> reasons = new ArrayList<>();

        // Track which row ids were consumed by exclusive factor scoring
        Set<String> consumedRowIds = new LinkedHashSet<>();

        for (ScorecardExclusiveBandModel.FactorBands factor : model.factors()) {
            if (!ScorecardExclusiveBandModel.MODE_EXCLUSIVE.equals(factor.mode())) {
                continue;
            }
            String parameter = factor.parameter();
            String source = factor.source();
            BigDecimal raw = ScorecardPolicyEngine.resolve(source, parameter, app, ctx);
            if (raw == null && "COMPUTED".equalsIgnoreCase(source)) {
                raw = ScorecardPolicyEngine.resolveComputed(parameter, parameterDefs, app, ctx);
            }
            String provenance = resolveProvenance(parameter, ctx);
            boolean nonAuth = ScorecardValueProvenance.isNonAuthoritative(provenance);
            BigDecimal value = raw;
            String stringValue = ScorecardPolicyEngine.resolveStringValue(source, parameter, app, ctx);
            if (nonAuth) {
                if (blockNonAuthoritative && !allowNonProductionDemoScoring) {
                    value = null;
                    stringValue = null;
                    nonAuthBlocked = true;
                }
            }

            MissingPolicyResolution policyRes = resolveMissingPolicy(parameter, factorPolicies, requiredFromHard);
            String missingPolicy = policyRes.policy();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("parameter", parameter);
            row.put("source", source);
            row.put("weightMetadataOnly", true);
            row.put("valueProvenance", provenance);
            row.put("authoritativeForDecision",
                    (value != null || (stringValue != null && !stringValue.isBlank()))
                            && ScorecardValueProvenance.isAuthoritative(provenance));
            row.put("missingDataPolicy", missingPolicy);
            row.put("missingDataPolicySource", policyRes.source());
            row.put("bandMode", factor.mode());
            row.put("factorMaxPoints", factor.maxPoints());
            // Closure invariant: factorMax is MAX of exclusive bands, never SUM of alternatives
            row.put("denominatorContribution", "FACTOR_MAX_EQUALS_MAX_BAND_POINTS");
            enrichCanonicalBinding(row, parameter, rowMaps);

            boolean physicallyAbsent = value == null && (stringValue == null || stringValue.isBlank());
            boolean requiredUnsatisfied = MISSING_REQUIRED.equals(missingPolicy)
                    && !ScorecardValueProvenance.canSatisfyRequired(provenance, allowNonProductionDemoScoring);
            boolean absent = physicallyAbsent || requiredUnsatisfied;
            if (requiredUnsatisfied && !physicallyAbsent) {
                // UNKNOWN/MISSING/blocked demo cannot satisfy REQUIRED even if a numeric placeholder exists
                value = null;
                stringValue = null;
            }
            if (absent) {
                if (MISSING_REQUIRED.equals(missingPolicy)) {
                    dataInsufficient = true;
                    row.put("matched", false);
                    row.put("pointsEarned", 0);
                    row.put("maxScore", factor.maxPoints());
                    row.put("valueUsed", null);
                    row.put("reason", "REQUIRED_VALUE_MISSING");
                    row.put("dataInsufficient", true);
                    paramResults.add(row);
                    reasons.add("Required scorecard factor missing authoritative value: " + parameter);
                    // REQUIRED missing: do not contribute to max (avoid silent depress)
                    continue;
                }
                if (MISSING_OPTIONAL_DEPRESS.equals(missingPolicy)) {
                    maxPoints += factor.maxPoints();
                    row.put("matched", false);
                    row.put("pointsEarned", 0);
                    row.put("maxScore", factor.maxPoints());
                    row.put("valueUsed", null);
                    paramResults.add(row);
                    for (var b : factor.bands()) consumedRowIds.add(b.rowId());
                    continue;
                }
                // OPTIONAL_SKIP — exclude from both earned and max
                row.put("matched", false);
                row.put("pointsEarned", 0);
                row.put("maxScore", 0);
                row.put("skippedMissingOptional", true);
                row.put("valueUsed", null);
                paramResults.add(row);
                for (var b : factor.bands()) consumedRowIds.add(b.rowId());
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
            row.put("valueUsed", value != null ? value.toPlainString() : stringValue);
            row.put("matchedBand", matched == null ? null : matched.originalCondition());
            row.put("matchedRowId", matched == null ? null : matched.rowId());
            row.put("condition", matched == null ? null : matched.originalCondition());
            row.put("score", matched == null ? 0 : matched.score());
            if (matched != null) {
                row.put("weight", matched.weightMetadata());
            }
            paramResults.add(row);
            for (var b : factor.bands()) consumedRowIds.add(b.rowId());
        }

        // Non-numeric / MATCH_OPTION / text rows not covered by exclusive model
        for (Map<String, Object> r : rowMaps) {
            String id = str(r.get("id"));
            if (id != null && consumedRowIds.contains(id)) continue;
            String parameter = str(r.get("parameter"));
            if (parameter == null) continue;
            // Already covered as exclusive factor?
            boolean covered = model.factors().stream()
                    .anyMatch(f -> parameter.equals(f.parameter())
                            && (str(r.get("source")) == null || str(r.get("source")).equals(f.source()))
                            && ScorecardExclusiveBandModel.MODE_EXCLUSIVE.equals(f.mode())
                            && f.bands().stream().anyMatch(b -> id != null && id.equals(b.rowId())));
            if (covered) continue;

            // Evaluate as single optional contribution (legacy MATCH_OPTION / text / non-ladder)
            String source = str(r.get("source"));
            String cond = str(r.get("condition"));
            int maxRow = intOr(r.get("score"), 0);
            Map<String, Object> def = parameterDefs.get(parameter);
            boolean matchOption = "MATCH_OPTION".equalsIgnoreCase(cond)
                    || (def != null && ScorecardPolicyEngine.isOptionScoredInputType(def)
                    && (cond == null || cond.isBlank()));
            boolean textTyped = def != null && "text".equalsIgnoreCase(str(def.get("inputType")));
            String stringValue = ScorecardPolicyEngine.resolveStringValue(source, parameter, app, ctx);
            BigDecimal v = ScorecardPolicyEngine.resolve(source, parameter, app, ctx);
            if (v == null && "COMPUTED".equalsIgnoreCase(source)) {
                v = ScorecardPolicyEngine.resolveComputed(parameter, parameterDefs, app, ctx);
            }
            String provenance = resolveProvenance(parameter, ctx);
            if (ScorecardValueProvenance.isNonAuthoritative(provenance)
                    && blockNonAuthoritative && !allowNonProductionDemoScoring) {
                v = null;
                stringValue = null;
                nonAuthBlocked = true;
            }
            boolean m;
            int add;
            String valueUsed;
            if (matchOption && def != null) {
                int optionMax = ScorecardPolicyEngine.maxOptionScore(def);
                if (optionMax > 0) {
                    maxRow = optionMax;
                }
                Integer optionScore = ScorecardPolicyEngine.lookupOptionScore(def, stringValue);
                m = optionScore != null;
                add = m ? optionScore : 0;
                valueUsed = stringValue;
            } else if (textTyped || ScorecardPolicyEngine.isStringCondition(cond, v, stringValue)) {
                m = ScorecardPolicyEngine.stringConditionMatches(cond, stringValue);
                add = m ? maxRow : 0;
                valueUsed = stringValue;
            } else {
                m = v != null && ScorecardPolicyEngine.conditionMatchesWithRef(cond, v, app, ctx);
                add = m ? maxRow : 0;
                valueUsed = v != null ? v.toPlainString() : stringValue;
            }
            maxPoints += Math.max(maxRow, 0);
            earned += add;
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("rowId", id);
            one.put("parameter", parameter);
            one.put("source", source);
            one.put("condition", cond);
            one.put("weightMetadataOnly", true);
            one.put("weight", intOr(r.get("weight"), 1));
            one.put("maxScore", maxRow);
            one.put("pointsEarned", add);
            one.put("matched", m);
            one.put("valueUsed", valueUsed);
            one.put("valueProvenance", provenance);
            one.put("authoritativeForDecision",
                    valueUsed != null && !ScorecardValueProvenance.isNonAuthoritative(provenance));
            one.put("bandMode", "LEGACY_SINGLE");
            enrichCanonicalBinding(one, parameter, rowMaps);
            paramResults.add(one);
        }

        Map<String, Object> t = card.getThresholdsJson() != null ? card.getThresholdsJson() : Map.of();
        int approveMin = intOr(t.get("approveMinPercent"), 70);
        int manualMin = intOr(t.get("manualMinPercent"), 40);

        if (dataInsufficient) {
            Map<String, Object> evidence = evidenceBase(card, earned, maxPoints, 50, approveMin, manualMin,
                    paramResults, blockNonAuthoritative, nonAuthBlocked);
            evidence.put("dataInsufficient", true);
            return new ScoreOutcome(earned, maxPoints, 50, "MANUAL_REVIEW", "MANUAL_REVIEW",
                    paramResults, evidence, true, nonAuthBlocked, false, reasons);
        }

        if (maxPoints <= 0) {
            Map<String, Object> evidence = evidenceBase(card, 0, 0, 50, approveMin, manualMin,
                    paramResults, blockNonAuthoritative, nonAuthBlocked);
            return new ScoreOutcome(0, 0, 50, "MANUAL_REVIEW", "MANUAL_REVIEW", paramResults, evidence,
                    false, nonAuthBlocked, false,
                    List.of("Scorecard has no scorable parameter capacity; manual review required."));
        }

        // Rounding: keep exact earned/max integers; only final percent uses HALF_UP to 0 decimal places
        int normalized = BigDecimal.valueOf(100L * earned)
                .divide(BigDecimal.valueOf(maxPoints), 0, RoundingMode.HALF_UP)
                .intValue();
        String policyDecision;
        String creditDecision;
        if (normalized >= approveMin) {
            policyDecision = "APPROVE";
            creditDecision = "APPROVED";
        } else if (normalized >= manualMin) {
            policyDecision = "MANUAL_REVIEW";
            creditDecision = "MANUAL_REVIEW";
        } else {
            policyDecision = "REJECT";
            creditDecision = "REJECTED";
        }

        Map<String, Object> evidence = evidenceBase(card, earned, maxPoints, normalized, approveMin, manualMin,
                paramResults, blockNonAuthoritative, nonAuthBlocked);
        if (nonAuthBlocked) {
            evidence.put("nonAuthoritativeValuesBlocked", true);
        }
        if (allowNonProductionDemoScoring) {
            evidence.put("evaluationAuthority", "DEMO_NON_PRODUCTION");
            evidence.put("authoritativeForDecision", false);
        } else {
            evidence.put("evaluationAuthority", "PRODUCTION");
            evidence.put("authoritativeForDecision", true);
        }
        return new ScoreOutcome(earned, maxPoints, normalized, policyDecision, creditDecision,
                paramResults, evidence, false, nonAuthBlocked, false, reasons);
    }

    private static Map<String, Object> evidenceBase(
            UnderwritingScorecard card,
            int earned,
            int maxPoints,
            int normalized,
            int approveMin,
            int manualMin,
            List<Map<String, Object>> paramResults,
            boolean blockNonAuth,
            boolean nonAuthBlocked) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("scorecardId", card.getId() == null ? null : card.getId().toString());
        evidence.put("scorecardName", card.getName());
        evidence.put("scorecardVersion", card.getVersion());
        evidence.put("scorecardStatus", card.getStatus());
        evidence.put("lineageId", card.getLineageId() == null ? null : card.getLineageId().toString());
        evidence.put("bandSemantics", ScorecardExclusiveBandModel.MODE_EXCLUSIVE);
        evidence.put("weightSemantics", "METADATA_ONLY_NOT_USED_IN_FORMULA");
        evidence.put("denominatorSemantics",
                "totalMaxPoints = SUM(factorMaxPoints); factorMaxPoints = MAX(points across mutually exclusive bands)");
        evidence.put("formula",
                "normalizedPercent = HALF_UP(100 * earned / maxPoints, 0 decimals); "
                        + "maxPoints = sum(MAX(band.score) per exclusive factor)");
        evidence.put("rounding", "HALF_UP to integer percent; no intermediate rounding of earned/max");
        evidence.put("earnedPoints", earned);
        evidence.put("maxPoints", maxPoints);
        evidence.put("normalizedPercent", normalized);
        evidence.put("approveMinPercent", approveMin);
        evidence.put("manualMinPercent", manualMin);
        evidence.put("parameterResults", paramResults);
        evidence.put("blockNonAuthoritativeDefaults", blockNonAuth);
        evidence.put("nonAuthoritativeValuesBlocked", nonAuthBlocked);
        return evidence;
    }

    private record MissingPolicyResolution(String policy, String source) {}

    private static MissingPolicyResolution resolveMissingPolicy(
            String parameter, Map<String, Object> factorPolicies, Set<String> requiredFromHard) {
        Object pol = factorPolicies.get(parameter);
        if (pol instanceof Map<?, ?> m && m.get("missingData") != null) {
            String configured = String.valueOf(m.get("missingData")).trim().toUpperCase(Locale.ROOT);
            if (MISSING_REQUIRED.equals(configured)
                    || MISSING_OPTIONAL_DEPRESS.equals(configured)
                    || MISSING_OPTIONAL_SKIP.equals(configured)) {
                return new MissingPolicyResolution(configured, "EXPLICIT");
            }
        }
        if (requiredFromHard.contains(parameter)) {
            return new MissingPolicyResolution(MISSING_REQUIRED, "HARD_RULE_REQUIRED");
        }
        // Legacy ACTIVE cards without explicit policies: preserve OPTIONAL_DEPRESS economics
        return new MissingPolicyResolution(MISSING_OPTIONAL_DEPRESS, "LEGACY_IMPLICIT_OPTIONAL_DEPRESS");
    }

    /** Package helper for tests / validator. */
    public static String effectiveMissingPolicy(
            String parameter, Map<String, Object> factorPolicies, boolean hardRuleOperand) {
        return resolveMissingPolicy(parameter, factorPolicies == null ? Map.of() : factorPolicies,
                hardRuleOperand ? Set.of(parameter) : Set.of()).policy();
    }

    /** SCORECARD-CONVERGENCE-1 — attach canonical id/version for evidence (runtime key unchanged). */
    private static void enrichCanonicalBinding(
            Map<String, Object> resultRow, String parameter, List<Map<String, Object>> rowMaps) {
        if (parameter == null || resultRow == null) return;
        Map<String, Object> sourceRow = null;
        for (Map<String, Object> r : rowMaps) {
            if (parameter.equals(str(r.get("parameter")))) {
                sourceRow = r;
                break;
            }
        }
        String canonicalId = sourceRow != null ? str(sourceRow.get("canonicalParameterId")) : null;
        Integer defVer = null;
        if (sourceRow != null && sourceRow.get("canonicalDefinitionVersion") instanceof Number n) {
            defVer = n.intValue();
        }
        String mappingStatus = sourceRow != null ? str(sourceRow.get("mappingStatus")) : null;
        if (canonicalId == null) {
            ScorecardCanonicalFactorMapper.Binding b = ScorecardCanonicalFactorMapper.resolve(parameter);
            canonicalId = b.canonicalParameterId();
            defVer = b.canonicalDefinitionVersion() > 0 ? b.canonicalDefinitionVersion() : null;
            mappingStatus = b.mappingStatus();
        }
        resultRow.put("legacyParameterKey", parameter);
        resultRow.put("canonicalParameterId", canonicalId);
        resultRow.put("canonicalDefinitionVersion", defVer);
        resultRow.put("mappingStatus", mappingStatus);
    }

    private static Set<String> hardRuleParameters(UnderwritingScorecard card) {
        Set<String> out = new LinkedHashSet<>();
        Map<String, Object> hardWrap = card.getHardRulesJson() != null ? card.getHardRulesJson() : Map.of();
        Object h = hardWrap.get("rules");
        if (h instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("parameter") != null) {
                    out.add(String.valueOf(m.get("parameter")).trim());
                }
            }
        }
        return out;
    }

    /** Package-visible for hard-rule provenance checks in {@link ScorecardPolicyEngine}. */
    public static String resolveProvenancePublic(String parameter, EffectiveUnderwritingContext ctx) {
        return resolveProvenance(parameter, ctx);
    }

    private static String resolveProvenance(String parameter, EffectiveUnderwritingContext ctx) {
        if (parameter == null) return ScorecardValueProvenance.UNKNOWN;
        if (ctx.scorecardProvenance() != null) {
            String p = ctx.scorecardProvenance().get(parameter);
            if (p != null) return p;
            p = ctx.scorecardProvenance().get(parameter.toUpperCase(Locale.ROOT));
            if (p != null) return p;
        }
        if ("BUREAU_SCORE".equalsIgnoreCase(parameter)) {
            if ("DEMO_FALLBACK".equalsIgnoreCase(ctx.bureauSource())) {
                return ScorecardValueProvenance.DEMO_DEFAULT;
            }
            if (ctx.bureauSource() == null || ctx.bureauSource().isBlank()) {
                return ScorecardValueProvenance.UNKNOWN;
            }
            return ScorecardValueProvenance.REAL_PROVIDER;
        }
        if ("KYC_QUALITY".equalsIgnoreCase(parameter) || "KYC_PASS".equalsIgnoreCase(parameter)) {
            if ("DEMO_FALLBACK".equalsIgnoreCase(ctx.kycSource())) {
                return ScorecardValueProvenance.DEMO_DEFAULT;
            }
            return ScorecardValueProvenance.DERIVED;
        }
        // Scorecard map value present without provenance: not authoritative for REQUIRED
        if (ctx.scorecard() != null
                && (ctx.scorecard().containsKey(parameter)
                || ctx.scorecard().containsKey(parameter.toUpperCase(Locale.ROOT)))) {
            return ScorecardValueProvenance.UNKNOWN;
        }
        return ScorecardValueProvenance.MISSING;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parseParameterDefs(Map<String, Object> scj) {
        Object raw = scj.get("parameterDefs");
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> m) {
                out.put(String.valueOf(e.getKey()), (Map<String, Object>) m);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static int intOr(Object o, int d) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return d;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return d;
        }
    }
}
