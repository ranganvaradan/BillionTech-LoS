package com.los.core.service.credit;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.service.underwriting.ScorecardPolicyEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves turnover-based limit sizing from the active underwriting rule set's {@code limitSizing} JSON.
 * Computes scorecard context for hard rules; optional sanction / CAM caps only when configured on the rule.
 */
@Service
@RequiredArgsConstructor
public class LimitSizingService {

    public static final String BAND_WITHIN_STANDARD = "WITHIN_STANDARD";
    public static final String BAND_SPECIAL_DEVIATION = "SPECIAL_DEVIATION";
    public static final String BAND_OVER_ABSOLUTE_CAP = "OVER_ABSOLUTE_CAP";

    private final UnderwritingRuleSetRepository ruleSetRepository;

    public record Result(
            LimitSizingConfig config,
            BigDecimal annualGstTurnover,
            BigDecimal turnoverBasedLimit,
            BigDecimal standardLimit,
            BigDecimal maxDeviationLimit,
            BigDecimal requestedAmount,
            BigDecimal cappedRecommendedAmount,
            String band) {
    }

    public Optional<LimitSizingConfig> resolveConfig(LoanApplication app) {
        return resolveConfig(app, null);
    }

    private Optional<LimitSizingConfig> resolveConfig(
            LoanApplication app, Map<String, BigDecimal> scorecard) {
        if (app == null || app.getBorrowerType() == null || app.getLoanProduct() == null) {
            return Optional.empty();
        }
        List<UnderwritingRuleSet> candidates = ruleSetRepository
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        app.getBorrowerType().name(), app.getLoanProduct());
        for (UnderwritingRuleSet rule : candidates) {
            if (!matchesAmountTenure(rule, app)) {
                continue;
            }
            Map<String, Object> rulesJson = rule.getRulesJson();
            EffectiveUnderwritingContext ctx = minimalContext(app, scorecard);
            Map<String, Map<String, Object>> parameterDefs = parameterDefs(rulesJson);
            for (LimitSizingConfig cfg : configsFromRulesJson(rulesJson)) {
                if (cfg.dependsOn() == null
                        || ScorecardPolicyEngine.dependencyGroupMatches(
                                cfg.dependsOn(), parameterDefs, app, ctx)) {
                    return Optional.of(cfg);
                }
            }
        }
        return Optional.empty();
    }

    public void applyComputedMetrics(LoanApplication app, Map<String, BigDecimal> scorecard) {
        if (app == null || scorecard == null) {
            return;
        }
        resolveConfig(app, scorecard).ifPresent(cfg -> {
            BigDecimal turnover = scorecard.get(cfg.turnoverParameter());
            if (turnover == null) {
                turnover = resolveTurnoverFromFinancialInfo(app, cfg.turnoverParameter());
            }
            Result result = compute(app, turnover, cfg);
            if (result != null) {
                putScorecardKeys(scorecard, result);
            }
        });
    }

    public Optional<Result> computeForApp(LoanApplication app) {
        return computeForApp(app, null);
    }

    /**
     * Same as {@link #computeForApp(LoanApplication)} but prefers turnover from an in-memory scorecard
     * (e.g. effective underwriting context) before falling back to persisted financialInfo.
     */
    public Optional<Result> computeForApp(LoanApplication app, Map<String, BigDecimal> scorecardHint) {
        return resolveConfig(app, scorecardHint).map(cfg -> {
            BigDecimal turnover = null;
            if (scorecardHint != null) {
                turnover = scorecardHint.get(cfg.turnoverParameter());
            }
            if (turnover == null) {
                turnover = resolveTurnoverFromFinancialInfo(app, cfg.turnoverParameter());
            }
            return compute(app, turnover, cfg);
        }).filter(r -> r != null);
    }

    public Map<String, Object> toMetaMap(Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("turnoverParameter", r.config().turnoverParameter());
        m.put("turnoverLimitPercent", plain(r.config().turnoverLimitPercent()));
        m.put("standardTicketCap", plain(r.config().standardTicketCap()));
        m.put("maxDeviationCap", plain(r.config().maxDeviationCap()));
        m.put("standardCapMode", r.config().standardCapMode().name());
        m.put("maxDeviationMode", r.config().maxDeviationMode().name());
        m.put("maxDeviationPercent", plain(r.config().maxDeviationPercent()));
        m.put("sanctionCapEnabled", r.config().sanctionCapEnabled());
        m.put("camRecommendedCapEnabled", r.config().camRecommendedCapEnabled());
        m.put("annualGstTurnover", plain(r.annualGstTurnover()));
        m.put("turnoverBasedLimit", plain(r.turnoverBasedLimit()));
        m.put("standardLimit", plain(r.standardLimit()));
        m.put("maxDeviationLimit", plain(r.maxDeviationLimit()));
        m.put("requestedAmount", plain(r.requestedAmount()));
        m.put("cappedRecommendedAmount", plain(r.cappedRecommendedAmount()));
        m.put("band", r.band());
        return m;
    }

    /** Auto-reduce sanctioned amount only when rule config enables it. */
    public boolean applySanctionCapIfConfigured(LoanApplication app) {
        Optional<LimitSizingConfig> cfgOpt = resolveConfig(app);
        if (cfgOpt.isEmpty() || !cfgOpt.get().sanctionCapEnabled()) {
            return false;
        }
        Result result = computeForApp(app).orElse(null);
        if (result == null) {
            return false;
        }
        BigDecimal current = app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
        if (current == null) {
            return false;
        }
        BigDecimal capped = current.min(result.standardLimit());
        if (app.getSanctionedAmount() == null || capped.compareTo(app.getSanctionedAmount()) != 0) {
            app.setSanctionedAmount(capped);
            return true;
        }
        return false;
    }

    public BigDecimal capRecommendedIfConfigured(LoanApplication app, BigDecimal recommended) {
        if (recommended == null) {
            return null;
        }
        Optional<LimitSizingConfig> cfgOpt = resolveConfig(app);
        if (cfgOpt.isEmpty() || !cfgOpt.get().camRecommendedCapEnabled()) {
            return recommended;
        }
        Result result = computeForApp(app).orElse(null);
        if (result == null) {
            return recommended;
        }
        return recommended.min(result.standardLimit());
    }

    public BigDecimal applicableStandardCap(LoanApplication app) {
        return computeForApp(app).map(Result::standardLimit).orElse(null);
    }

    public static Result compute(LoanApplication app, BigDecimal turnover, LimitSizingConfig cfg) {
        if (cfg == null || app == null) {
            return null;
        }
        boolean turnoverRequired = cfg.standardCapMode() != LimitSizingConfig.StandardCapMode.FIXED
                || cfg.maxDeviationMode() == LimitSizingConfig.MaxDeviationMode.TURNOVER_PERCENT;
        if (turnoverRequired && (turnover == null || turnover.compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
        BigDecimal turnoverBased = turnover == null || cfg.turnoverLimitPercent() == null
                ? null
                : percentOf(turnover, cfg.turnoverLimitPercent());
        BigDecimal standardLimit = switch (cfg.standardCapMode()) {
            case MIN_OF_BOTH -> turnoverBased.min(cfg.standardTicketCap());
            case TURNOVER_PERCENT -> turnoverBased;
            case FIXED -> cfg.standardTicketCap();
        };
        BigDecimal maxDeviationLimit = switch (cfg.maxDeviationMode()) {
            case FIXED -> cfg.maxDeviationCap();
            case TURNOVER_PERCENT -> percentOf(turnover, cfg.maxDeviationPercent());
        };
        BigDecimal requested = app.getRequestedAmount();
        BigDecimal cappedRecommended = requested == null ? standardLimit : requested.min(standardLimit);
        String band = BAND_WITHIN_STANDARD;
        if (requested != null) {
            if (requested.compareTo(maxDeviationLimit) > 0) {
                band = BAND_OVER_ABSOLUTE_CAP;
            } else if (requested.compareTo(standardLimit) > 0) {
                band = BAND_SPECIAL_DEVIATION;
            }
        }
        return new Result(
                cfg,
                turnover,
                turnoverBased,
                standardLimit,
                maxDeviationLimit,
                requested,
                cappedRecommended,
                band);
    }

    public static void putScorecardKeys(Map<String, BigDecimal> sc, Result r) {
        if (sc == null || r == null) {
            return;
        }
        sc.put("SCF_STANDARD_LIMIT", r.standardLimit());
        sc.put("SCF_MAX_DEVIATION_LIMIT", r.maxDeviationLimit());
        sc.put("SCF_CAPPED_AMOUNT", r.cappedRecommendedAmount());
        sc.put("SCF_ELIGIBLE_AMOUNT", r.standardLimit());
        if (r.requestedAmount() != null) {
            BigDecimal over = r.requestedAmount().subtract(r.standardLimit());
            if (over.compareTo(BigDecimal.ZERO) < 0) {
                over = BigDecimal.ZERO;
            }
            sc.put("SCF_AMOUNT_OVER_STANDARD", over);
        }
        int bandCode = switch (r.band()) {
            case BAND_SPECIAL_DEVIATION -> 1;
            case BAND_OVER_ABSOLUTE_CAP -> 2;
            default -> 0;
        };
        sc.put("SCF_LIMIT_BAND", BigDecimal.valueOf(bandCode));
        if (r.annualGstTurnover() != null) {
            sc.put(r.config().turnoverParameter(), r.annualGstTurnover());
        }
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<LimitSizingConfig> configsFromRulesJson(Map<String, Object> rulesJson) {
        if (rulesJson == null || rulesJson.isEmpty()) {
            return List.of();
        }
        Object raw = rulesJson.get("limitSizing");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(LimitSizingConfig::fromMap)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        if (raw instanceof Map<?, ?> map) {
            LimitSizingConfig cfg = LimitSizingConfig.fromMap(map);
            return cfg == null ? List.of() : List.of(cfg);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parameterDefs(Map<String, Object> rulesJson) {
        if (rulesJson == null || !(rulesJson.get("parameterDefs") instanceof Map<?, ?> defs)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> parsed = new LinkedHashMap<>();
        defs.forEach((key, value) -> {
            if (key != null && value instanceof Map<?, ?> map) {
                parsed.put(String.valueOf(key), (Map<String, Object>) map);
            }
        });
        return parsed;
    }

    private static EffectiveUnderwritingContext minimalContext(
            LoanApplication app, Map<String, BigDecimal> scorecard) {
        Map<String, BigDecimal> sc = scorecard == null ? Map.of() : scorecard;
        BigDecimal bureau = sc.get("BUREAU_SCORE");
        BigDecimal kyc = sc.get("KYC_QUALITY");
        BigDecimal income = sc.get("MONTHLY_INCOME");
        BigDecimal obligation = sc.get("EMI_OBLIGATION");
        if (obligation == null) {
            obligation = sc.get("MONTHLY_OBLIGATION");
        }
        String state = personalInfoValue(app, "state");
        String city = personalInfoValue(app, "city");
        return new EffectiveUnderwritingContext(
                bureau == null ? 0 : bureau.intValue(),
                kyc != null && kyc.compareTo(BigDecimal.ZERO) > 0,
                income,
                obligation,
                state,
                city,
                null,
                null,
                null,
                sc);
    }

    private static String personalInfoValue(LoanApplication app, String key) {
        if (app.getPersonalInfo() == null || app.getPersonalInfo().get(key) == null) {
            return null;
        }
        return String.valueOf(app.getPersonalInfo().get(key));
    }

    @SuppressWarnings("unchecked")
    static BigDecimal resolveTurnoverFromFinancialInfo(LoanApplication app, String parameter) {
        Map<String, Object> fi = app.getFinancialInfo();
        if (fi == null) {
            return null;
        }
        Object meta = fi.get("underwritingMeta");
        if (meta instanceof Map<?, ?> um) {
            Object policy = um.get("limitSizingPolicy");
            if (policy instanceof Map<?, ?> p) {
                BigDecimal fromMeta = toBd(p.get("annualGstTurnover"));
                if (fromMeta != null && fromMeta.compareTo(BigDecimal.ZERO) > 0) {
                    return fromMeta;
                }
            }
        }
        Object cc = fi.get("creditControl");
        if (cc instanceof Map<?, ?> ccm) {
            Object scorecard = ccm.get("scorecard");
            if (scorecard instanceof Map<?, ?> sc) {
                BigDecimal fromSc = toBd(sc.get(parameter));
                if (fromSc != null && fromSc.compareTo(BigDecimal.ZERO) > 0) {
                    return fromSc;
                }
            }
            Object manual = ccm.get("manualCreditInputs");
            if (manual instanceof Map<?, ?> m) {
                BigDecimal fromManual = toBd(m.get("annualGstTurnover"));
                if (fromManual != null && fromManual.compareTo(BigDecimal.ZERO) > 0) {
                    return fromManual;
                }
            }
        }
        return null;
    }

    private static boolean matchesAmountTenure(UnderwritingRuleSet r, LoanApplication app) {
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
        return true;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static BigDecimal toBd(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
