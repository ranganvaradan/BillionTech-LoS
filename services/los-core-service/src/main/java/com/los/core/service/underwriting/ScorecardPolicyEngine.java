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

        for (Map<String, Object> hr : hard) {
            String p = str(hr.get("parameter"));
            String source = str(hr.get("source"));
            String cond = str(hr.get("condition"));
            BigDecimal v = resolve(source, p, app, ctx);
            if (conditionMatches(cond, v)) {
                String dec = str(hr.get("decision"));
                String msg = str(hr.get("message"));
                if (msg == null || msg.isBlank()) {
                    msg = str(hr.get("reason"));
                }
                if (msg == null || msg.isBlank()) {
                    msg = "Hard rule triggered on " + p;
                }
                if ("REJECT".equalsIgnoreCase(dec) || "REJECTED".equalsIgnoreCase(dec)) {
                    return finishHard(c, app, ctx, kycMeta, "REJECT", "REJECTED", 0, List.of(msg), false);
                }
                if ("MANUAL_REVIEW".equalsIgnoreCase(dec) || "MANUAL".equalsIgnoreCase(dec)) {
                    return finishHard(
                            c, app, ctx, kycMeta, "MANUAL_REVIEW", "MANUAL_REVIEW", 50, List.of(msg), false);
                }
            }
        }

        Map<String, Object> scj = c.getScorecardJson() != null ? c.getScorecardJson() : Map.of();
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
            maxPoints += maxRow;
            BigDecimal v = resolve(source, p, app, ctx);
            boolean m = conditionMatches(cond, v);
            int add = m ? maxRow : 0;
            earned += add;
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("rowId", str(row.get("id")));
            one.put("parameter", p);
            one.put("source", source);
            one.put("condition", cond);
            one.put("weight", w);
            one.put("maxScore", maxRow);
            one.put("valueUsed", v != null ? v.toPlainString() : null);
            one.put("valueSource", describeSource(source, p, ctx, app));
            one.put("matched", m);
            one.put("pointsEarned", add);
            one.put("attachment", str(row.get("attachment")));
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
        if ("CONTEXT".equalsIgnoreCase(source)) {
            if ("MONTHLY_INCOME".equalsIgnoreCase(param)) {
                return "ctx:" + ctx.incomeSource();
            }
            if ("MONTHLY_OBLIGATION".equalsIgnoreCase(param) || "EMI_OBLIGATION".equalsIgnoreCase(param)) {
                return "ctx:obligation";
            }
        }
        if ("MANUAL_OR_PROVIDER".equalsIgnoreCase(source) || "GST".equalsIgnoreCase(source)
                || "BANK_STATEMENT".equalsIgnoreCase(source) || "VALUATION".equalsIgnoreCase(source)
                || "FINANCIALS".equalsIgnoreCase(source) || "MANUAL_OR_SYSTEM".equalsIgnoreCase(source)) {
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
        return source != null ? source : "—";
    }

    private static BigDecimal resolve(
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

    private static boolean conditionMatches(String cond, BigDecimal v) {
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
}
