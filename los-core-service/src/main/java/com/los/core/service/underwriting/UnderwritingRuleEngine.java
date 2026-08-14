package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Selects <strong>all</strong> active {@link UnderwritingRuleSet} rows matching borrower, product, and optional
 * amount / tenure / geography; evaluates each; aggregates recommendation.
 */
@Service
@RequiredArgsConstructor
public class UnderwritingRuleEngine {

    private final UnderwritingRuleSetRepository ruleSetRepository;
    @SuppressWarnings("unused")
    private final KycStepResultRepository kycStepResultRepository;

    /**
     * @deprecated use {@link #evaluateAll(LoanApplication, EffectiveUnderwritingContext, String)} — kept for tests
     *             expecting first matching rule only.
     */
    @Deprecated
    public Optional<RuleApplicationResult> findAndApply(LoanApplication app, String kycOutcome) {
        int bureau = effectiveBureauLegacy(app);
        var ctx = new EffectiveUnderwritingContext(
                bureau, "PASS".equalsIgnoreCase(String.valueOf(kycOutcome).trim()), null, null, null, null,
                "PROVIDER", "PROVIDER", "PROVIDER", Map.of());
        MultiRuleEvalResult m = evaluateAll(app, ctx, kycOutcome);
        if (m.perRule().isEmpty()) {
            return Optional.empty();
        }
        var first = m.perRule().get(0);
        UnderwritingRuleSet rule = ruleSetRepository.findById(UUID.fromString(first.ruleId())).orElse(null);
        if (rule == null) {
            return Optional.empty();
        }
        return Optional.of(new RuleApplicationResult(
                rule,
                first.creditDecision(),
                first.policyDecision(),
                first.riskScore(),
                first.reasons(),
                true,
                first.kind()
        ));
    }

    public MultiRuleEvalResult evaluateAll(LoanApplication app, EffectiveUnderwritingContext ctx, String kycOutcomeForMeta) {
        List<UnderwritingRuleSet> candidates = ruleSetRepository
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        app.getBorrowerType().name(), app.getLoanProduct());
        List<MultiRuleEvalResult.PerRuleEval> out = new ArrayList<>();
        for (UnderwritingRuleSet r : candidates) {
            if (!matchesFilters(r, app, ctx)) {
                continue;
            }
            out.add(evaluateOne(r, app, ctx, kycOutcomeForMeta));
        }
        return aggregate(out);
    }

    private static MultiRuleEvalResult aggregate(List<MultiRuleEvalResult.PerRuleEval> per) {
        if (per.isEmpty()) {
            return new MultiRuleEvalResult(List.of(), "NONE", "NONE", 0, List.of());
        }
        boolean anyReject = per.stream()
                .anyMatch(p -> "REJECTED".equals(p.creditDecision()) || "REJECT".equals(p.policyDecision()));
        boolean anyManual = per.stream()
                .anyMatch(p -> "MANUAL_REVIEW".equals(p.creditDecision()) || "MANUAL_REVIEW".equals(p.policyDecision()));
        if (anyReject) {
            List<String> reasons = new ArrayList<>();
            for (var p : per) {
                if ("REJECTED".equals(p.creditDecision()) || "REJECT".equals(p.policyDecision())) {
                    reasons.addAll(p.reasons() != null ? p.reasons() : List.of());
                }
            }
            int sc = per.stream().mapToInt(MultiRuleEvalResult.PerRuleEval::riskScore).min().orElse(0);
            return new MultiRuleEvalResult(per, "REJECT", "REJECTED", sc, reasons.isEmpty() ? List.of("Policy: reject") : reasons);
        }
        if (anyManual) {
            int sc = Math.round((float) per.stream().mapToInt(MultiRuleEvalResult.PerRuleEval::riskScore).average().orElse(50));
            return new MultiRuleEvalResult(per, "MANUAL_REVIEW", "MANUAL_REVIEW", sc, List.of("At least one rule requires manual review"));
        }
        boolean allApprove = per.stream().allMatch(p -> "APPROVED".equals(p.creditDecision()) || "APPROVE".equals(p.policyDecision()));
        if (allApprove) {
            int sc = Math.round((float) per.stream().mapToInt(MultiRuleEvalResult.PerRuleEval::riskScore).average().orElse(0));
            return new MultiRuleEvalResult(per, "APPROVE", "APPROVED", sc, List.of());
        }
        int sc = Math.round((float) per.stream().mapToInt(MultiRuleEvalResult.PerRuleEval::riskScore).average().orElse(50));
        return new MultiRuleEvalResult(per, "MANUAL_REVIEW", "MANUAL_REVIEW", sc, List.of("Mixed rule outcomes; manual review"));
    }

    private boolean matchesFilters(UnderwritingRuleSet r, LoanApplication app, EffectiveUnderwritingContext ctx) {
        BigDecimal req = app.getRequestedAmount();
        if (r.getMinAmount() != null && (req == null || req.compareTo(r.getMinAmount()) < 0)) {
            return false;
        }
        if (r.getMaxAmount() != null && (req == null || req.compareTo(r.getMaxAmount()) > 0)) {
            return false;
        }
        Integer tm = app.getTenureMonths();
        if (r.getMinTenureMonths() != null && (tm == null || tm < r.getMinTenureMonths())) {
            return false;
        }
        if (r.getMaxTenureMonths() != null && (tm == null || tm > r.getMaxTenureMonths())) {
            return false;
        }
        if (r.getGeography() != null && !r.getGeography().isEmpty()) {
            return matchesGeography(r.getGeography(), app, ctx);
        }
        return true;
    }

    private boolean matchesGeography(Map<String, Object> filter, LoanApplication app, EffectiveUnderwritingContext ctx) {
        Map<String, Object> pi = effectivePersonalForRules(app, ctx);
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

    private static Map<String, Object> effectivePersonalForRules(LoanApplication app, EffectiveUnderwritingContext ctx) {
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

    private MultiRuleEvalResult.PerRuleEval evaluateOne(UnderwritingRuleSet rule, LoanApplication app, EffectiveUnderwritingContext ctx, String kycMeta) {
        Map<String, Object> j = rule.getRulesJson() != null ? rule.getRulesJson() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> parameterDefs = j.get("parameterDefs") instanceof Map<?, ?> defs
                ? defs.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getValue() instanceof Map<?, ?>)
                        .collect(java.util.stream.Collectors.toMap(
                                e -> String.valueOf(e.getKey()).trim(),
                                e -> new LinkedHashMap<>((Map<String, Object>) e.getValue()),
                                (a, b) -> b,
                                LinkedHashMap::new))
                : Map.of();
        List<String> outReasons = new ArrayList<>();
        List<String> fromJson = toReasonList(j.get("reasons"));
        if (!fromJson.isEmpty()) {
            outReasons.addAll(fromJson);
        }

        int effectiveBureau = ctx.effectiveBureauScore();
        boolean kycPass = ctx.kycPassEffective();

        Map<String, Object> matched = new LinkedHashMap<>();
        matched.put("borrowerType", app.getBorrowerType().name());
        matched.put("loanProduct", app.getLoanProduct());
        if (app.getRequestedAmount() != null) {
            matched.put("requestedAmount", app.getRequestedAmount().toPlainString());
        }
        if (app.getTenureMonths() != null) {
            matched.put("tenureMonths", app.getTenureMonths());
        }
        Map<String, Object> src = new LinkedHashMap<>(ctx.toMap());
        src.put("kycOutcomeForRules", kycMeta);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hardRules = j.get("hardRules") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList()
                : List.of();
        for (Map<String, Object> hardRule : hardRules) {
            String parameter = str(hardRule.get("parameter"));
            String source = str(hardRule.get("source"));
            String condition = str(hardRule.get("condition"));
            BigDecimal value = ScorecardPolicyEngine.resolve(source, parameter, app, ctx);
            if (value == null && "COMPUTED".equalsIgnoreCase(source)) {
                value = ScorecardPolicyEngine.resolveComputed(parameter, parameterDefs, app, ctx);
            }
            if (!ScorecardPolicyEngine.dependencyGroupMatches(hardRule.get("dependsOn"), parameterDefs, app, ctx)) {
                continue;
            }
            if (!ScorecardPolicyEngine.conditionMatchesWithRef(condition, value, app, ctx)) {
                continue;
            }
            String dec = str(hardRule.get("decision"));
            String msg = str(hardRule.get("message"));
            if (msg == null || msg.isBlank()) {
                msg = "Hard rule triggered on " + parameter;
            }
            msg = ScorecardPolicyEngine.hardRuleFailureMessage(parameter, condition, value, msg);
            if ("REJECT".equalsIgnoreCase(dec) || "REJECTED".equalsIgnoreCase(dec)) {
                return perRuleFrom(rule, "REJECT", "REJECTED", 0, List.of(msg), "HARD_RULE", matched, src);
            }
            if ("MANUAL_REVIEW".equalsIgnoreCase(dec) || "MANUAL".equalsIgnoreCase(dec)) {
                return perRuleFrom(rule, "MANUAL_REVIEW", "MANUAL_REVIEW", 50, List.of(msg), "HARD_RULE", matched, src);
            }
        }

        Integer minBureau = intOrNull(j.get("minBureauScore"));
        if (minBureau != null) {
            if (effectiveBureau < minBureau) {
                outReasons.add(0, "Bureau score " + effectiveBureau + " is below policy minimum " + minBureau);
                return perRuleFrom(rule, "REJECT", "REJECTED", 20, outReasons, "CONSTRAINT", matched, src);
            }
        }

        BigDecimal maxLoan = toBd(j.get("maxLoanAmount"));
        if (maxLoan != null && app.getRequestedAmount() != null
                && app.getRequestedAmount().compareTo(maxLoan) > 0) {
            outReasons.add(0, "Requested amount " + app.getRequestedAmount() + " exceeds policy maximum " + maxLoan);
            return perRuleFrom(rule, "REJECT", "REJECTED", 15, outReasons, "CONSTRAINT", matched, src);
        }

        if (j.get("requireKycSuccess") instanceof Boolean b && b && !kycPass) {
            outReasons.add(0, "KYC must be PASS for this policy");
            return perRuleFrom(rule, "REJECT", "REJECTED", 10, outReasons, "CONSTRAINT", matched, src);
        }

        if (j.get("scorecardRules") instanceof List<?> srules && !srules.isEmpty()) {
            return evaluateScorecardRules(rule, srules, ctx, outReasons, effectiveBureau, matched, src);
        }

        String d = j.get("decision") != null ? j.get("decision").toString().trim().toUpperCase(Locale.ROOT) : "MANUAL_REVIEW";
        return switch (d) {
            case "APPROVE" -> perRuleFrom(rule, "APPROVE", "APPROVED", riskFromBureau(effectiveBureau), outReasons, "POLICY", matched, src);
            case "REJECT" -> {
                if (outReasons.isEmpty()) {
                    outReasons.add("Policy decision: REJECT");
                }
                yield perRuleFrom(rule, "REJECT", "REJECTED", 0, outReasons, "POLICY", matched, src);
            }
            case "MANUAL_REVIEW" -> perRuleFrom(rule, "MANUAL_REVIEW", "MANUAL_REVIEW", 50, outReasons, "POLICY", matched, src);
            default -> perRuleFrom(rule, d, "MANUAL_REVIEW", 50, outReasons, "POLICY", matched, src);
        };
    }

    @SuppressWarnings("unchecked")
    private MultiRuleEvalResult.PerRuleEval evaluateScorecardRules(
            UnderwritingRuleSet rule,
            List<?> srules,
            EffectiveUnderwritingContext ctx,
            List<String> outReasons,
            int effectiveBureau,
            Map<String, Object> matched,
            Map<String, Object> src) {
        int approveW = 0;
        int manualW = 0;
        int rejectW = 0;
        for (Object o : srules) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> line = (Map<String, Object>) o;
            String param = str(line.get("parameter"));
            Integer w = intOrNull(line.get("weight"));
            if (w == null || w <= 0) {
                continue;
            }
            String mode = str(line.get("mode"));
            if (mode == null) {
                mode = "GTE";
            }
            BigDecimal av = toBd(line.get("approveAt"));
            BigDecimal mv = toBd(line.get("manualAt"));
            BigDecimal v = resolveParameter(param, ctx);
            if (v == null) {
                outReasons.add("Scorecard: missing " + param);
                rejectW += w;
                continue;
            }
            int bucket; // 1 approve, 2 manual, 3 reject
            if ("LTE".equalsIgnoreCase(mode)) {
                if (av != null && v.compareTo(av) <= 0) {
                    bucket = 1;
                } else if (mv != null && v.compareTo(mv) <= 0) {
                    bucket = 2;
                } else {
                    bucket = 3;
                }
            } else { // GTE
                if (av != null && v.compareTo(av) >= 0) {
                    bucket = 1;
                } else if (mv != null && v.compareTo(mv) >= 0) {
                    bucket = 2;
                } else {
                    bucket = 3;
                }
            }
            if (bucket == 1) {
                approveW += w;
            } else if (bucket == 2) {
                manualW += w;
            } else {
                rejectW += w;
            }
        }
        if (approveW == 0 && manualW == 0 && rejectW == 0) {
            outReasons.add("Scorecard: no valid weighted rules");
            return perRuleFrom(rule, "MANUAL_REVIEW", "MANUAL_REVIEW", 50, outReasons, "SCORECARD", matched, src);
        }
        if (rejectW >= approveW && rejectW >= manualW && rejectW > 0) {
            outReasons.add(0, "Scorecard: reject bucket has highest weight");
            return perRuleFrom(rule, "REJECT", "REJECTED", 15, outReasons, "SCORECARD", matched, src);
        }
        if (manualW >= approveW && manualW > 0) {
            outReasons.add(0, "Scorecard: manual review bucket has highest weight");
            return perRuleFrom(rule, "MANUAL_REVIEW", "MANUAL_REVIEW", 50, outReasons, "SCORECARD", matched, src);
        }
        if (approveW > 0) {
            return perRuleFrom(
                    rule, "APPROVE", "APPROVED", riskFromBureau(effectiveBureau), outReasons, "SCORECARD", matched, src);
        }
        return perRuleFrom(rule, "MANUAL_REVIEW", "MANUAL_REVIEW", 50, outReasons, "SCORECARD", matched, src);
    }

    private static BigDecimal resolveParameter(String param, EffectiveUnderwritingContext ctx) {
        if (param == null || param.isEmpty()) {
            return null;
        }
        if ("BUREAU_SCORE".equalsIgnoreCase(param)) {
            return BigDecimal.valueOf(ctx.effectiveBureauScore());
        }
        if ("MONTHLY_INCOME".equalsIgnoreCase(param)) {
            return ctx.effectiveIncome();
        }
        if ("EMI_OBLIGATION".equalsIgnoreCase(param) || "MONTHLY_OBLIGATION".equalsIgnoreCase(param)) {
            return ctx.effectiveObligation();
        }
        if (ctx.scorecard() == null) {
            return null;
        }
        return ctx.scorecard().get(param);
    }

    private static MultiRuleEvalResult.PerRuleEval perRuleFrom(
            UnderwritingRuleSet rule,
            String policyDecision,
            String creditDecision,
            int risk,
            List<String> reasons,
            String kind,
            Map<String, Object> matched,
            Map<String, Object> source) {
        return new MultiRuleEvalResult.PerRuleEval(
                rule.getId().toString(),
                rule.getName(),
                policyDecision,
                creditDecision,
                risk,
                reasons,
                kind,
                matched,
                source);
    }

    private int effectiveBureauLegacy(LoanApplication app) {
        if (app.getManualBureauScore() != null && app.getManualBureauScore() > 0) {
            return app.getManualBureauScore();
        }
        if (app.getBureauScore() != null
                && (app.getBureauScore() > 0 || app.getBureauScore() == -1)) {
            return app.getBureauScore();
        }
        return 0;
    }

    private static int riskFromBureau(int score) {
        if (score <= 0) {
            return 50;
        }
        return Math.max(0, Math.min(100, Math.round((score / 900f) * 100)));
    }

    private static List<String> toReasonList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> list) {
            List<String> s = new ArrayList<>();
            for (Object e : list) {
                if (e != null) {
                    s.add(e.toString());
                }
            }
            return s;
        }
        return List.of();
    }

    private static Integer intOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    public record RuleApplicationResult(
            UnderwritingRuleSet rule,
            String creditDecision,
            String policyDecision,
            int riskScore,
            List<String> reasons,
            boolean fromRule,
            String kind) {
    }
}
