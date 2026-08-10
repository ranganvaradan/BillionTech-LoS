package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Picks the highest-priority active scorecard matching borrower/product/amount/geo, evaluates hard rules, parameters,
 * and thresholds, and maps the outcome to {@link MultiRuleEvalResult} for a single “virtual” policy row.
 */
@Service
@RequiredArgsConstructor
public class ScorecardPolicyEngine {

    private final UnderwritingScorecardRepository scorecardRepository;

    public record ScorecardEvalResult(
            MultiRuleEvalResult multi,
            UUID scorecardId,
            List<Map<String, Object>> parameterResults) {
    }

    public Optional<ScorecardEvalResult> evaluate(LoanApplication app, EffectiveUnderwritingContext ctx, String kycMeta) {
        List<UnderwritingScorecard> cands = scorecardRepository
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        app.getBorrowerType().name(), app.getLoanProduct());
        for (UnderwritingScorecard c : cands) {
            if (matchesScope(c, app, ctx)) {
                return Optional.of(build(c, app, ctx, kycMeta));
            }
        }
        return Optional.empty();
    }

    private boolean matchesScope(UnderwritingScorecard c, LoanApplication app, EffectiveUnderwritingContext ctx) {
        BigDecimal req = app.getRequestedAmount();
        if (c.getMinAmount() != null && (req == null || req.compareTo(c.getMinAmount()) < 0)) {
            return false;
        }
        if (c.getMaxAmount() != null && (req == null || req.compareTo(c.getMaxAmount()) > 0)) {
            return false;
        }
        if (c.getGeography() != null && !c.getGeography().isEmpty()) {
            return matchesGeography(c.getGeography(), app, ctx);
        }
        return true;
    }

    private boolean matchesGeography(Map<String, Object> filter, LoanApplication app, EffectiveUnderwritingContext ctx) {
        Map<String, Object> pi = effectivePersonal(app, ctx);
        if (pi == null) {
            return false;
        }
        if (filter.containsKey("state") && filter.get("state") != null) {
            String want = filter.get("state").toString().trim();
            String have = str(pi.get("state"));
            if (have == null || !have.equalsIgnoreCase(want)) {
                return false;
            }
        }
        if (filter.containsKey("city") && filter.get("city") != null) {
            String want = filter.get("city").toString().trim();
            String have = str(pi.get("city"));
            if (have == null || !have.equalsIgnoreCase(want)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> effectivePersonal(LoanApplication app, EffectiveUnderwritingContext ctx) {
        Map<String, Object> pi = new LinkedHashMap<>();
        if (app.getPersonalInfo() != null) {
            pi.putAll(app.getPersonalInfo());
        }
        if (ctx.effectiveState() != null) {
            pi.put("state", ctx.effectiveState());
        }
        if (ctx.effectiveCity() != null) {
            pi.put("city", ctx.effectiveCity());
        }
        return pi.isEmpty() ? null : pi;
    }

    @SuppressWarnings("unchecked")
    private ScorecardEvalResult build(UnderwritingScorecard c, LoanApplication app, EffectiveUnderwritingContext ctx, String kycMeta) {
        Map<String, Object> t = c.getThresholdsJson() != null ? c.getThresholdsJson() : Map.of();
        int approveMin = intOrDefault(t.get("approveMinPercent"), 70);
        int manualMin = intOrDefault(t.get("manualMinPercent"), 40);

        Map<String, Object> hardWrap = c.getHardRulesJson() != null ? c.getHardRulesJson() : Map.of();
        List<Map<String, Object>> hard = new ArrayList<>();
        Object h = hardWrap.get("rules");
        if (h instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map) {
                    hard.add((Map<String, Object>) o);
                }
            }
        }
        List<Map<String, Object>> hardTrace = new ArrayList<>();

        Map<String, Object> scj = c.getScorecardJson() != null ? c.getScorecardJson() : Map.of();
        Map<String, Map<String, Object>> parameterDefs = parseParameterDefs(scj);

        for (Map<String, Object> hr : hard) {
            String p = str(hr.get("parameter"));
            String source = str(hr.get("source"));
            String cond = str(hr.get("condition"));
            Map<String, Object> dependencyOutcome = evaluateDependencyGroup(hr.get("dependsOn"), parameterDefs, app, ctx);
            boolean depsMatched = !(dependencyOutcome.get("matched") instanceof Boolean b) || b;
            BigDecimal v = resolve(source, p, app, ctx);
            if (v == null && "COMPUTED".equalsIgnoreCase(source)) {
                v = resolveComputed(p, parameterDefs, app, ctx);
            }
            boolean hardMatched = depsMatched && conditionMatchesWithRef(cond, v, app, ctx);
            Map<String, Object> traceRow = new LinkedHashMap<>();
            traceRow.put("rowId", str(hr.get("id")));
            traceRow.put("parameter", p);
            traceRow.put("source", source);
            traceRow.put("condition", cond);
            traceRow.put("valueUsed", v != null ? v.toPlainString() : null);
            traceRow.put("matched", hardMatched);
            traceRow.put("decision", str(hr.get("decision")));
            traceRow.put("reason", str(hr.get("reason")));
            traceRow.put("pointsEarned", 0);
            traceRow.put("maxScore", 0);
            traceRow.put("hardRule", true);
            traceRow.put("dependencyOutcome", dependencyOutcome);
            traceRow.put("skippedDueToDependency", !depsMatched);
            Map<String, Object> breakdown = formulaBreakdown(p, parameterDefs, app, ctx);
            if (breakdown != null) {
                traceRow.put("formulaBreakdown", breakdown);
            }
            hardTrace.add(traceRow);
            if (!depsMatched) {
                continue;
            }
            if (hardMatched) {
                String dec = str(hr.get("decision"));
                String msg = str(hr.get("message"));
                if (msg == null || msg.isBlank()) {
                    msg = str(hr.get("reason"));
                }
                if (msg == null || msg.isBlank()) {
                    msg = "Hard rule triggered on " + p;
                }
                msg = hardRuleFailureMessage(p, cond, v, msg);
                if ("REJECT".equalsIgnoreCase(dec) || "REJECTED".equalsIgnoreCase(dec)) {
                    return finishHard(
                            c, app, ctx, kycMeta, "REJECT", "REJECTED", 0, List.of(msg), true, hardTrace);
                }
                if ("MANUAL_REVIEW".equalsIgnoreCase(dec) || "MANUAL".equalsIgnoreCase(dec)) {
                    return finishHard(
                            c,
                            app,
                            ctx,
                            kycMeta,
                            "MANUAL_REVIEW",
                            "MANUAL_REVIEW",
                            50,
                            List.of(msg),
                            true,
                            hardTrace);
                }
            }
        }

        List<Map<String, Object>> rowMaps = new ArrayList<>();
        Object rows = scj.get("rows");
        if (rows instanceof List<?> rlist) {
            for (Object o : rlist) {
                if (o instanceof Map) {
                    rowMaps.add((Map<String, Object>) o);
                }
            }
        }

        int maxPoints = 0;
        int earned = 0;
        List<Map<String, Object>> paramResults = new ArrayList<>();
        for (Map<String, Object> row : rowMaps) {
            String p = str(row.get("parameter"));
            String source = str(row.get("source"));
            String cond = str(row.get("condition"));
            int w = intOrNull(row.get("weight"));
            if (w <= 0) {
                w = 1;
            }
            int maxRow = intOrNull(row.get("score"));
            if (maxRow < 0) {
                maxRow = 0;
            }

            Map<String, Object> def = parameterDefs.get(p);
            boolean matchOption = "MATCH_OPTION".equalsIgnoreCase(cond)
                    || (def != null && isOptionScoredInputType(def) && (cond == null || cond.isBlank()));
            boolean textTyped = def != null && "text".equalsIgnoreCase(str(def.get("inputType")));

            String stringValue = resolveStringValue(source, p, app, ctx);
            BigDecimal v = resolve(source, p, app, ctx);
            if (v == null && "COMPUTED".equalsIgnoreCase(source)) {
                v = resolveComputed(p, parameterDefs, app, ctx);
            }
            Map<String, Object> dependencyOutcome = evaluateDependencyGroup(row.get("dependsOn"), parameterDefs, app, ctx);
            boolean depsMatched = !(dependencyOutcome.get("matched") instanceof Boolean b) || b;
            Map<String, Object> breakdown = formulaBreakdown(p, parameterDefs, app, ctx);
            if (!depsMatched) {
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("rowId", str(row.get("id")));
                skipped.put("parameter", p);
                skipped.put("source", source);
                skipped.put("condition", cond);
                skipped.put("weight", w);
                skipped.put("maxScore", maxRow);
                skipped.put("valueUsed", v != null ? v.toPlainString() : stringValue);
                skipped.put("valueSource", describeSource(source, p, ctx, app));
                skipped.put("matched", false);
                skipped.put("pointsEarned", 0);
                skipped.put("attachment", str(row.get("attachment")));
                skipped.put("dependencyOutcome", dependencyOutcome);
                skipped.put("skippedDueToDependency", true);
                if (breakdown != null) {
                    skipped.put("formulaBreakdown", breakdown);
                }
                paramResults.add(skipped);
                continue;
            }
            boolean m;
            int add;
            String valueUsed;

            if (matchOption && def != null) {
                int optionMax = maxOptionScore(def);
                if (optionMax > 0) {
                    maxRow = optionMax;
                }
                maxPoints += maxRow;
                Integer optionScore = lookupOptionScore(def, stringValue);
                m = optionScore != null;
                add = m ? optionScore : 0;
                valueUsed = stringValue;
            } else if (textTyped || isStringCondition(cond, v, stringValue)) {
                maxPoints += maxRow;
                m = stringConditionMatches(cond, stringValue);
                add = m ? maxRow : 0;
                valueUsed = stringValue;
            } else {
                maxPoints += maxRow;
                m = conditionMatchesWithRef(cond, v, app, ctx);
                add = m ? maxRow : 0;
                valueUsed = v != null ? v.toPlainString() : stringValue;
            }
            earned += add;
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("rowId", str(row.get("id")));
            one.put("parameter", p);
            one.put("source", source);
            one.put("condition", cond);
            one.put("weight", w);
            one.put("maxScore", maxRow);
            one.put("valueUsed", valueUsed);
            one.put("valueSource", describeSource(source, p, ctx, app));
            one.put("matched", m);
            one.put("pointsEarned", add);
            one.put("attachment", str(row.get("attachment")));
            one.put("dependencyOutcome", dependencyOutcome);
            one.put("skippedDueToDependency", false);
            if (breakdown != null) {
                one.put("formulaBreakdown", breakdown);
            }
            paramResults.add(one);
        }

        if (maxPoints == 0) {
            return finishHard(
                    c,
                    app,
                    ctx,
                    kycMeta,
                    "MANUAL_REVIEW",
                    "MANUAL_REVIEW",
                    50,
                    List.of("Scorecard has no parameter rows; manual review required."),
                    true,
                    paramResults);
        }

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

        Map<String, Object> matched = new LinkedHashMap<>();
        matched.put("engine", "STRUCTURED_SCORECARD");
        matched.put("scorecardId", c.getId().toString());
        matched.put("scorecardName", c.getName());
        matched.put("scorecardVersion", c.getVersion());
        matched.put("normalizedPercent", normalized);
        matched.put("earnedPoints", earned);
        matched.put("maxPoints", maxPoints);
        matched.put("approveMinPercent", approveMin);
        matched.put("manualMinPercent", manualMin);
        matched.put("kycOutcomeForRules", kycMeta);
        matched.put("parameterResults", paramResults);

        Map<String, Object> src = new LinkedHashMap<>(ctx.toMap());
        src.put("kycOutcomeForRules", kycMeta);

        MultiRuleEvalResult.PerRuleEval per = new MultiRuleEvalResult.PerRuleEval(
                c.getId().toString(),
                c.getName(),
                policyDecision,
                creditDecision,
                normalized,
                List.of(),
                "SCORECARD",
                matched,
                src);
        var multi = new MultiRuleEvalResult(
                List.of(per), policyDecision, creditDecision, normalized, List.of());
        return new ScorecardEvalResult(multi, c.getId(), paramResults);
    }

    private ScorecardEvalResult finishHard(
            UnderwritingScorecard c,
            LoanApplication app,
            EffectiveUnderwritingContext ctx,
            String kycMeta,
            String policy,
            String creditAgg,
            int risk,
            List<String> reasons,
            boolean includeEmptyParams) {
        return finishHard(c, app, ctx, kycMeta, policy, creditAgg, risk, reasons, includeEmptyParams, List.of());
    }

    private ScorecardEvalResult finishHard(
            UnderwritingScorecard c,
            LoanApplication app,
            EffectiveUnderwritingContext ctx,
            String kycMeta,
            String policy,
            String creditAgg,
            int risk,
            List<String> reasons,
            boolean includeEmptyParams,
            List<Map<String, Object>> paramResults) {
        Map<String, Object> matched = new LinkedHashMap<>();
        matched.put("engine", "STRUCTURED_SCORECARD");
        matched.put("scorecardId", c.getId().toString());
        matched.put("scorecardName", c.getName());
        matched.put("hardRule", true);
        if (includeEmptyParams) {
            matched.put("parameterResults", paramResults);
        }
        Map<String, Object> src = new LinkedHashMap<>(ctx.toMap());
        src.put("kycOutcomeForRules", kycMeta);
        var per = new MultiRuleEvalResult.PerRuleEval(
                c.getId().toString(),
                c.getName(),
                policy,
                creditAgg,
                risk,
                reasons,
                "SCORECARD",
                matched,
                src);
        var multi = new MultiRuleEvalResult(List.of(per), policy, creditAgg, risk, reasons);
        return new ScorecardEvalResult(multi, c.getId(), paramResults);
    }

    private static String describeSource(String source, String param, EffectiveUnderwritingContext ctx, LoanApplication app) {
        if ("BUREAU".equalsIgnoreCase(source)) {
            return "ctx:" + ctx.bureauSource();
        }
        if ("KYC".equalsIgnoreCase(source)) {
            return "ctx:" + ctx.kycSource();
        }
        if ("SCORECARD".equalsIgnoreCase(source) && param != null) {
            return "scorecard:" + param;
        }
        if ("APPLICATION".equalsIgnoreCase(source)) {
            return "application";
        }
        if ("PROGRAM_INPUTS".equalsIgnoreCase(source)) {
            return "programInputs→creditControl.scorecard[" + (param != null ? param : "") + "]";
        }
        if ("CONTEXT".equalsIgnoreCase(source)) {
            if ("MONTHLY_INCOME".equalsIgnoreCase(param)) {
                return "ctx:" + ctx.incomeSource();
            }
            if ("MONTHLY_OBLIGATION".equalsIgnoreCase(param) || "EMI_OBLIGATION".equalsIgnoreCase(param)) {
                return "ctx:obligation";
            }
        }
        if ("MANUAL_OR_PROVIDER".equalsIgnoreCase(source) || "GST".equalsIgnoreCase(source)
                || "GST_STATEMENT".equalsIgnoreCase(source) || "OTHER".equalsIgnoreCase(source)
                || "BANK_STATEMENT".equalsIgnoreCase(source) || "ITR".equalsIgnoreCase(source)
                || "VALUATION".equalsIgnoreCase(source)
                || "FINANCIALS".equalsIgnoreCase(source) || "MANUAL_OR_SYSTEM".equalsIgnoreCase(source)
                || "PROGRAM_INPUTS".equalsIgnoreCase(source)) {
            return (source != null ? source : "ROW")
                    + "→creditControl.scorecard["
                    + (param != null ? param : "")
                    + "]";
        }
        if ("SYSTEM".equalsIgnoreCase(source)) {
            if ("KYC_QUALITY".equalsIgnoreCase(param)) {
                return "ctx:kycEffective";
            }
            return "system";
        }
        if ("COMPUTED".equalsIgnoreCase(source)) {
            return "computed:" + (param != null ? param : "formula");
        }
        return source != null ? source : "—";
    }

    static BigDecimal resolve(
            String source, String param, LoanApplication app, EffectiveUnderwritingContext ctx) {
        if (param == null) {
            return null;
        }
        String src = source != null ? source.trim().toUpperCase(Locale.ROOT) : "BUREAU";
        if ("BUREAU".equals(src) && "BUREAU_SCORE".equalsIgnoreCase(param)) {
            return BigDecimal.valueOf(ctx.effectiveBureauScore());
        }
        if ("KYC".equals(src) && "KYC_PASS".equalsIgnoreCase(param)) {
            return ctx.kycPassEffective() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if ("APPLICATION".equals(src)) {
            if ("REQUESTED_AMOUNT".equalsIgnoreCase(param) && app.getRequestedAmount() != null) {
                return app.getRequestedAmount();
            }
            if ("TENURE_MONTHS".equalsIgnoreCase(param) && app.getTenureMonths() != null) {
                return BigDecimal.valueOf(app.getTenureMonths());
            }
            BigDecimal applicationParam = ApplicationScorecardParameterResolver.resolve(param, app, null);
            if (applicationParam != null) {
                return applicationParam;
            }
        }
        if ("CONTEXT".equals(src)) {
            if ("MONTHLY_INCOME".equalsIgnoreCase(param) || "EFFECTIVE_INCOME".equalsIgnoreCase(param)) {
                return ctx.effectiveIncome();
            }
            if ("MONTHLY_OBLIGATION".equalsIgnoreCase(param) || "EMI_OBLIGATION".equalsIgnoreCase(param)) {
                return ctx.effectiveObligation();
            }
            if ("DTI_RATIO".equalsIgnoreCase(param) || "OBLIGATION_TO_INCOME".equalsIgnoreCase(param)) {
                if (ctx.effectiveIncome() == null
                        || ctx.effectiveIncome().compareTo(BigDecimal.ZERO) <= 0
                        || ctx.effectiveObligation() == null) {
                    return null;
                }
                return ctx
                        .effectiveObligation()
                        .divide(ctx.effectiveIncome(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
        }
        if (ctx.scorecard() != null && param != null) {
            BigDecimal z = ctx.scorecard().get(param);
            if (z == null) {
                z = ctx.scorecard().get(param.toUpperCase(Locale.ROOT));
            }
            if (z != null) {
                return z;
            }
        }
        return null;
    }

    /**
     * Resolve a COMPUTED parameter by evaluating its formula using already-resolved operand values.
     */
    @SuppressWarnings("unchecked")
    static BigDecimal resolveComputed(
            String param,
            Map<String, Map<String, Object>> parameterDefs,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        if (param == null || parameterDefs == null) return null;
        Map<String, Object> def = parameterDefs.get(param);
        if (def == null) return null;
        if (!"formula".equalsIgnoreCase(str(def.get("inputType")))) return null;
        Object formulaRaw = def.get("formula");
        if (!(formulaRaw instanceof Map<?, ?> formulaMap)) return null;
        String expression = str(formulaMap.get("expression"));
        if (expression == null || expression.isBlank()) return null;
        Object operandsRaw = formulaMap.get("operands");
        if (!(operandsRaw instanceof List<?> operandsList)) return null;
        Map<String, BigDecimal> vars = new LinkedHashMap<>();
        for (Object opRaw : operandsList) {
            if (!(opRaw instanceof Map<?, ?> opMap)) continue;
            String opParam = str(opMap.get("parameter"));
            String opSource = str(opMap.get("source"));
            if (opParam == null) continue;
            BigDecimal val = resolve(opSource, opParam, app, ctx);
            if (val == null) return null;
            vars.put(opParam, val);
        }
        return FormulaEvaluator.evaluate(expression, vars);
    }

    /**
     * Condition matching with parameter reference support.
     * PARAM_REF conditions compare against another resolved parameter instead of a fixed value.
     * Format: PARAM_REF:OPERATOR:SOURCE:PARAMETER
     */
    static boolean conditionMatchesWithRef(
            String cond, BigDecimal v, LoanApplication app, EffectiveUnderwritingContext ctx) {
        if (cond == null || cond.isBlank() || v == null) {
            return false;
        }
        String c = cond.trim();
        if (c.toUpperCase(Locale.ROOT).startsWith("PARAM_REF:")) {
            String rest = c.substring("PARAM_REF:".length()).trim();
            String[] parts = rest.split(":", 3);
            if (parts.length < 3) return false;
            String op = parts[0].trim().toUpperCase(Locale.ROOT);
            String refSource = parts[1].trim();
            String refParam = parts[2].trim();
            BigDecimal refValue = resolve(refSource, refParam, app, ctx);
            if (refValue == null) return false;
            return switch (op) {
                case "GTE" -> v.compareTo(refValue) >= 0;
                case "GT" -> v.compareTo(refValue) > 0;
                case "LTE" -> v.compareTo(refValue) <= 0;
                case "LT" -> v.compareTo(refValue) < 0;
                case "EQ" -> v.compareTo(refValue) == 0;
                case "NE" -> v.compareTo(refValue) != 0;
                default -> false;
            };
        }
        return conditionMatches(cond, v);
    }

    @SuppressWarnings("unchecked")
    public static boolean dependencyGroupMatches(
            Object raw,
            Map<String, Map<String, Object>> parameterDefs,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        Map<String, Object> outcome = evaluateDependencyGroup(raw, parameterDefs, app, ctx);
        Object matched = outcome.get("matched");
        return !(matched instanceof Boolean b) || b;
    }

    /**
     * Evaluate dependency group and return a UI-friendly outcome map:
     * { matched, logic, conditions: [{ source, parameter, condition, valueUsed, matched }] }.
     * When no dependencies are configured, matched=true and conditions=[].
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> evaluateDependencyGroup(
            Object raw,
            Map<String, Map<String, Object>> parameterDefs,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matched", true);
        out.put("logic", "ALL");
        out.put("conditions", List.of());
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        Object conditionsRaw = map.get("conditions");
        if (!(conditionsRaw instanceof List<?> conditions) || conditions.isEmpty()) {
            return out;
        }
        String logic = str(map.get("logic"));
        boolean any = "ANY".equalsIgnoreCase(logic);
        out.put("logic", any ? "ANY" : "ALL");
        List<Map<String, Object>> conditionRows = new ArrayList<>();
        boolean matchedAny = false;
        boolean matchedAll = true;
        for (Object item : conditions) {
            if (!(item instanceof Map<?, ?> dep)) {
                continue;
            }
            String source = str(dep.get("source"));
            String parameter = str(dep.get("parameter"));
            String condition = str(dep.get("condition"));
            BigDecimal value = resolve(source, parameter, app, ctx);
            if (value == null && "COMPUTED".equalsIgnoreCase(source)) {
                value = resolveComputed(parameter, parameterDefs, app, ctx);
            }
            boolean matched = conditionMatchesWithRef(condition, value, app, ctx);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", source);
            row.put("parameter", parameter);
            row.put("condition", condition);
            row.put("valueUsed", value != null ? value.toPlainString() : null);
            row.put("matched", matched);
            conditionRows.add(row);
            matchedAny = matchedAny || matched;
            if (!matched) {
                matchedAll = false;
            }
        }
        out.put("conditions", conditionRows);
        out.put("matched", any ? matchedAny : matchedAll);
        return out;
    }

    /**
     * Build a UI-friendly formula breakdown for COMPUTED parameters.
     * Returns null when the parameter is not formula-backed.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> formulaBreakdown(
            String param,
            Map<String, Map<String, Object>> parameterDefs,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        if (param == null || parameterDefs == null) {
            return null;
        }
        Map<String, Object> def = parameterDefs.get(param);
        if (def == null || !"formula".equalsIgnoreCase(str(def.get("inputType")))) {
            return null;
        }
        Object formulaRaw = def.get("formula");
        if (!(formulaRaw instanceof Map<?, ?> formulaMap)) {
            return null;
        }
        String expression = str(formulaMap.get("expression"));
        Object operandsRaw = formulaMap.get("operands");
        List<Map<String, Object>> operandRows = new ArrayList<>();
        Map<String, BigDecimal> vars = new LinkedHashMap<>();
        if (operandsRaw instanceof List<?> operandsList) {
            for (Object opRaw : operandsList) {
                if (!(opRaw instanceof Map<?, ?> opMap)) {
                    continue;
                }
                String opParam = str(opMap.get("parameter"));
                String opSource = str(opMap.get("source"));
                if (opParam == null) {
                    continue;
                }
                BigDecimal val = resolve(opSource, opParam, app, ctx);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("source", opSource);
                row.put("parameter", opParam);
                row.put("valueUsed", val != null ? val.toPlainString() : null);
                operandRows.add(row);
                if (val != null) {
                    vars.put(opParam, val);
                }
            }
        }
        BigDecimal result = expression != null && !expression.isBlank() ? FormulaEvaluator.evaluate(expression, vars) : null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expression", expression);
        out.put("operands", operandRows);
        out.put("result", result != null ? result.toPlainString() : null);
        return out;
    }

    static boolean conditionMatches(String cond, BigDecimal v) {
        if (cond == null || cond.isBlank() || v == null) {
            return false;
        }
        String c = cond.trim();
        int first = c.indexOf(':');
        if (first < 0) {
            return false;
        }
        String op = c.substring(0, first).trim().toUpperCase(Locale.ROOT);
        String rest = c.substring(first + 1).trim();
        try {
            if ("BETWEEN".equals(op)) {
                int mid = rest.indexOf(':');
                if (mid < 0) {
                    return false;
                }
                BigDecimal a = new BigDecimal(rest.substring(0, mid).trim());
                BigDecimal b = new BigDecimal(rest.substring(mid + 1).trim());
                return v.compareTo(a) >= 0 && v.compareTo(b) <= 0;
            }
            if ("NE".equals(op)) {
                return !eqOrCompareNumericOrEnum(v, rest);
            }
            if ("GTE".equals(op)
                    || "LTE".equals(op)
                    || "GT".equals(op)
                    || "LT".equals(op)
                    || "EQ".equals(op)) {
                return eqOrCompareNumericOrEnum(v, rest, op);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /** EQ/GTE/… with numeric right-hand side, or EQ/NE with enum tokens PASS/LOW/CLEAN (v is 0/1 coded). */
    private static boolean eqOrCompareNumericOrEnum(BigDecimal v, String rest) {
        return eqOrCompareNumericOrEnum(v, rest, "EQ");
    }

    private static boolean eqOrCompareNumericOrEnum(BigDecimal v, String rest, String op) {
        if (v == null) {
            return false;
        }
        String rhsS = rest;
        int extra = rest.indexOf(':');
        if (extra > 0) {
            rhsS = rest.substring(0, extra);
        }
        rhsS = rhsS.trim();
        if ("EQ".equals(op) || "NE".equals(op)) {
            if ("PASS".equalsIgnoreCase(rhsS)) {
                return v.compareTo(BigDecimal.ONE) == 0;
            }
            if ("CLEAN".equalsIgnoreCase(rhsS)) {
                return v.compareTo(BigDecimal.ONE) == 0;
            }
            if ("LOW".equalsIgnoreCase(rhsS)) {
                return v.compareTo(BigDecimal.ONE) == 0;
            }
        }
        if ("true".equalsIgnoreCase(rhsS)) {
            rhsS = "1";
        } else if ("false".equalsIgnoreCase(rhsS)) {
            rhsS = "0";
        } else if ("Y".equalsIgnoreCase(rhsS) || "YES".equalsIgnoreCase(rhsS)) {
            rhsS = "1";
        } else if ("N".equalsIgnoreCase(rhsS) || "NO".equalsIgnoreCase(rhsS)) {
            rhsS = "0";
        }
        BigDecimal rhs = new BigDecimal(rhsS);
        return switch (op) {
            case "GTE" -> v.compareTo(rhs) >= 0;
            case "GT" -> v.compareTo(rhs) > 0;
            case "LTE" -> v.compareTo(rhs) <= 0;
            case "LT" -> v.compareTo(rhs) < 0;
            case "EQ" -> v.compareTo(rhs) == 0;
            case "NE" -> v.compareTo(rhs) != 0;
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parseParameterDefs(Map<String, Object> scorecardJson) {
        Object raw = scorecardJson.get("parameterDefs");
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null || !(e.getValue() instanceof Map<?, ?> def)) {
                continue;
            }
            String key = String.valueOf(e.getKey()).trim();
            if (key.isEmpty()) {
                continue;
            }
            out.put(key, new LinkedHashMap<>((Map<String, Object>) def));
        }
        return out;
    }

    private static boolean isOptionScoredInputType(Map<String, Object> def) {
        String inputType = str(def.get("inputType"));
        return "dropdown".equalsIgnoreCase(inputType);
    }

    private static int maxOptionScore(Map<String, Object> def) {
        Object optionsRaw = def.get("options");
        if (!(optionsRaw instanceof List<?> list)) {
            return 0;
        }
        int max = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                max = Math.max(max, intOrNull(m.get("score")));
            }
        }
        return max;
    }

    private static Integer lookupOptionScore(Map<String, Object> def, String collected) {
        if (collected == null || collected.isBlank()) {
            return null;
        }
        String trimmed = collected.trim();
        Object optionsRaw = def.get("options");
        if (!(optionsRaw instanceof List<?> list)) {
            return null;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String value = str(m.get("value"));
            String label = str(m.get("label"));
            if (trimmed.equals(value) || (label != null && trimmed.equalsIgnoreCase(label))) {
                return intOrNull(m.get("score"));
            }
        }
        return null;
    }

    private static boolean isStringCondition(String cond, BigDecimal numericValue, String stringValue) {
        if (cond == null || cond.isBlank() || stringValue == null || stringValue.isBlank()) {
            return false;
        }
        String c = cond.trim();
        int first = c.indexOf(':');
        if (first < 0) {
            return false;
        }
        String op = c.substring(0, first).trim().toUpperCase(Locale.ROOT);
        if (!"EQ".equals(op) && !"NE".equals(op) && !"CONTAINS".equals(op) && !"NOT_CONTAINS".equals(op)) {
            return false;
        }
        String rest = c.substring(first + 1).trim();
        if (numericValue == null) {
            return true;
        }
        if ("CONTAINS".equals(op) || "NOT_CONTAINS".equals(op)) {
            return true;
        }
        try {
            new BigDecimal(rest);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean stringConditionMatches(String cond, String stringValue) {
        if (cond == null || cond.isBlank() || stringValue == null) {
            return false;
        }
        String c = cond.trim();
        int first = c.indexOf(':');
        if (first < 0) {
            return false;
        }
        String op = c.substring(0, first).trim().toUpperCase(Locale.ROOT);
        String rest = c.substring(first + 1).trim();
        String left = stringValue.trim();
        boolean eq = left.equalsIgnoreCase(rest);
        if ("EQ".equals(op)) {
            return eq;
        }
        if ("NE".equals(op)) {
            return !eq;
        }
        boolean contains = left.toLowerCase(Locale.ROOT).contains(rest.toLowerCase(Locale.ROOT));
        if ("CONTAINS".equals(op)) {
            return contains;
        }
        if ("NOT_CONTAINS".equals(op)) {
            return !contains;
        }
        return false;
    }

    private static String resolveStringValue(
            String source, String param, LoanApplication app, EffectiveUnderwritingContext ctx) {
        if (param == null) {
            return null;
        }
        String fromApp = ApplicationScorecardParameterResolver.resolveString(param, app);
        if (fromApp != null && !fromApp.isBlank()) {
            return fromApp;
        }
        String fromMetrics = readManualScorecardMetricString(app, param);
        if (fromMetrics != null && !fromMetrics.isBlank()) {
            return fromMetrics;
        }
        if (ctx.scorecard() != null) {
            BigDecimal z = ctx.scorecard().get(param);
            if (z == null) {
                z = ctx.scorecard().get(param.toUpperCase(Locale.ROOT));
            }
            if (z != null) {
                return z.toPlainString();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String readManualScorecardMetricString(LoanApplication app, String param) {
        if (app.getFinancialInfo() == null) {
            return null;
        }
        Object ccRaw = app.getFinancialInfo().get("creditControl");
        if (!(ccRaw instanceof Map<?, ?> cc)) {
            return null;
        }
        Object manualRaw = cc.get("manual");
        if (!(manualRaw instanceof Map<?, ?> manual)) {
            return null;
        }
        Object metricsRaw = manual.get("scorecardMetrics");
        if (!(metricsRaw instanceof Map<?, ?> metrics)) {
            return unwrapManualString(manual.get(param));
        }
        String fromNested = unwrapManualString(metrics.get(param));
        if (fromNested != null) {
            return fromNested;
        }
        return unwrapManualString(manual.get(param));
    }

    private static String unwrapManualString(Object cell) {
        if (cell == null) {
            return null;
        }
        if (cell instanceof Map<?, ?> m && m.get("value") != null) {
            return String.valueOf(m.get("value")).trim();
        }
        String s = cell.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOrNull(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int intOrDefault(Object o, int def) {
        int n = intOrNull(o);
        return n > 0 ? n : def;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    static String hardRuleFailureMessage(String parameter, String condition, BigDecimal value, String reason) {
        StringBuilder out = new StringBuilder();
        if (reason != null && !reason.isBlank()) {
            out.append(reason.trim());
        } else {
            out.append("Hard rule failed");
        }
        out.append(" — ");
        out.append(parameter != null && !parameter.isBlank() ? parameter : "parameter");
        out.append(" value ");
        out.append(value != null ? value.toPlainString() : "missing");
        if (condition != null && !condition.isBlank()) {
            out.append(" triggered condition ").append(condition.trim());
        }
        return out.toString();
    }
}
