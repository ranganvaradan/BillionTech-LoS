package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiLimitMethodResult;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.domain.LimitCombineStrategy;
import com.los.core.creditintelligence.decision.domain.RecommendationOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-method limit sizing — combine per strategy (never silent max of turnover sources).
 */
@Service
public class LimitMethodEngine {

    public static final String DATA_AVAILABLE = "AVAILABLE";
    public static final String DATA_INSUFFICIENT = "DATA_INSUFFICIENT";
    public static final String DATA_NOT_APPLICABLE = "NOT_APPLICABLE";

    public record LimitResult(
            List<CiLimitMethodResult> candidates,
            String selectedMethod,
            BigDecimal selectedEligibleAmount,
            BigDecimal recommendedAmount,
            BigDecimal amountReduction,
            RecommendationOutcome amountOutcome,
            List<String> reasonCodes,
            Map<String, Object> detail
    ) {}

    public LimitResult compute(Map<String, Object> limitStrategy, DecisionRuntimeInput input) {
        Map<String, Object> strategy = limitStrategy == null ? Map.of() : limitStrategy;
        Map<String, Object> params = DecisionValueHelper.asMap(strategy.get("params"));
        @SuppressWarnings("unchecked")
        List<String> methods = strategy.get("methods") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : List.of("REQUESTED_AMOUNT");

        List<CiLimitMethodResult> candidates = new ArrayList<>();
        for (String method : methods) {
            candidates.add(computeMethod(method, params, input));
        }

        LimitCombineStrategy combine = parseCombine(strategy.get("combine"));
        String primary = DecisionValueHelper.str(strategy.get("primaryMethod"));
        CombineOutcome combined = combine(candidates, combine, primary, params);

        BigDecimal requested = input.requestedAmount() == null ? BigDecimal.ZERO : input.requestedAmount();
        BigDecimal eligible = combined.amount() == null ? BigDecimal.ZERO : combined.amount();
        BigDecimal recommended;
        RecommendationOutcome outcome;
        List<String> reasons = new ArrayList<>();
        reasons.addAll(combined.reasons());

        if (DATA_INSUFFICIENT.equals(combined.dataStatus()) && eligible.signum() == 0
                && combined.amount() == null) {
            recommended = null;
            outcome = RecommendationOutcome.DATA_INSUFFICIENT;
            reasons.add("LIMIT_DATA_INSUFFICIENT");
        } else if (requested.signum() > 0 && eligible.compareTo(requested) < 0) {
            recommended = eligible.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            outcome = RecommendationOutcome.COUNTER_OFFER;
            reasons.add("AMOUNT_REDUCED_TO_ELIGIBLE");
            reasons.add("BINDING_METHOD:" + combined.method());
        } else if (requested.signum() > 0) {
            recommended = requested.setScale(2, RoundingMode.HALF_UP);
            outcome = RecommendationOutcome.APPROVE;
            reasons.add("REQUESTED_WITHIN_ELIGIBLE");
            reasons.add("BINDING_METHOD:" + combined.method());
        } else {
            recommended = eligible.setScale(2, RoundingMode.HALF_UP);
            outcome = RecommendationOutcome.APPROVE;
            reasons.add("NO_REQUESTED_AMOUNT_USE_ELIGIBLE");
        }

        BigDecimal reduction = requested.signum() > 0 && recommended != null
                ? requested.subtract(recommended).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requestedAmount", requested);
        detail.put("candidateLimits", candidates.stream().map(this::toMap).toList());
        detail.put("selectedLimitMethod", combined.method());
        detail.put("selectedEligibleAmount", eligible);
        detail.put("recommendedAmount", recommended);
        detail.put("amountReduction", reduction);
        detail.put("combine", combine.name());
        detail.put("combineDataStatus", combined.dataStatus());

        return new LimitResult(candidates, combined.method(), eligible, recommended, reduction,
                outcome, reasons, detail);
    }

    private CiLimitMethodResult computeMethod(String method, Map<String, Object> params, DecisionRuntimeInput input) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "TURNOVER_LIMIT" -> turnoverLimit(params, input);
            case "BANKING_CREDIT_LIMIT" -> bankingLimit(params, input);
            case "CASH_FLOW_LIMIT" -> cashFlowLimit(params, input);
            case "FOIR_LIMIT" -> foirLimit(params, input);
            case "DSCR_LIMIT" -> dscrLimit(params, input);
            case "COLLATERAL_LIMIT" -> collateralLimit(params, input);
            case "INVOICE_FINANCE_LIMIT" -> invoiceLimit(params, input);
            case "POLICY_CAP" -> policyCap(params, input);
            case "REQUESTED_AMOUNT" -> requestedAmount(input);
            default -> CiLimitMethodResult.builder()
                    .methodCode(method)
                    .eligibleAmount(null)
                    .formulaVersion("UNKNOWN")
                    .dataStatus(DATA_NOT_APPLICABLE)
                    .inputs(Map.of("error", "UNKNOWN_METHOD"))
                    .evidenceRefs(List.of())
                    .detail(Map.of())
                    .build();
        };
    }

    private CiLimitMethodResult turnoverLimit(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal pct = DecisionValueHelper.bd(params.getOrDefault("turnoverPct", 0.20));
        String source = String.valueOf(params.getOrDefault("turnoverSource", "TRIANGULATED")).toUpperCase(Locale.ROOT);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("turnoverPct", pct);
        inputs.put("turnoverSource", source);

        BigDecimal turnover = resolveTurnover(source, input, inputs);
        if (turnover == null) {
            return CiLimitMethodResult.builder()
                    .methodCode("TURNOVER_LIMIT")
                    .eligibleAmount(null)
                    .formulaVersion("TURNOVER_PCT_V1")
                    .dataStatus(DATA_INSUFFICIENT)
                    .inputs(inputs)
                    .evidenceRefs(List.of(Map.of("kind", "METRIC", "reference", "turnover")))
                    .detail(Map.of("reason", "TURNOVER_SOURCE_MISSING"))
                    .build();
        }
        BigDecimal eligible = turnover.multiply(pct).setScale(2, RoundingMode.HALF_UP);
        inputs.put("turnover", turnover);
        return CiLimitMethodResult.builder()
                .methodCode("TURNOVER_LIMIT")
                .eligibleAmount(eligible)
                .formulaVersion("TURNOVER_PCT_V1")
                .dataStatus(DATA_AVAILABLE)
                .inputs(inputs)
                .evidenceRefs(List.of(Map.of("kind", "METRIC", "reference", "turnover", "source", source)))
                .detail(Map.of("formula", "turnover * turnoverPct"))
                .build();
    }

    private BigDecimal resolveTurnover(String source, DecisionRuntimeInput input, Map<String, Object> inputs) {
        BigDecimal gst = DecisionValueHelper.num(input.metrics(), "gst.turnover.trailing_12m");
        if (gst == null) {
            gst = DecisionValueHelper.num(input.metrics(), "turnover.gst");
        }
        BigDecimal itr = DecisionValueHelper.num(input.metrics(), "itr.turnover.trailing_12m");
        if (itr == null) {
            itr = DecisionValueHelper.num(input.metrics(), "turnover.itr");
        }
        BigDecimal bank = DecisionValueHelper.num(input.metrics(), "bank.turnover.trailing_12m");
        if (bank == null) {
            bank = DecisionValueHelper.num(input.metrics(), "banking.turnover.trailing_12m");
        }
        if (bank == null) {
            bank = DecisionValueHelper.num(input.metrics(), "turnover.bank");
        }
        BigDecimal triangulated = DecisionValueHelper.num(input.metrics(), "turnover.triangulated");
        if (triangulated == null) {
            Object recon = input.reconciliations().get("TURNOVER_TRIANGULATION");
            if (recon instanceof Map<?, ?> rm) {
                triangulated = DecisionValueHelper.bd(rm.get("value") != null ? rm.get("value") : rm.get("triangulated"));
            }
        }
        inputs.put("gst", gst);
        inputs.put("itr", itr);
        inputs.put("bank", bank);
        inputs.put("triangulated", triangulated);

        return switch (source) {
            case "GST" -> gst;
            case "ITR" -> itr;
            case "BANK" -> bank;
            case "TRIANGULATED" -> triangulated != null ? triangulated
                    : (gst != null && itr != null && bank != null
                    ? gst.min(itr).min(bank) : null);
            case "MIN_OF_SOURCES" -> {
                List<BigDecimal> vals = new ArrayList<>();
                if (gst != null) vals.add(gst);
                if (itr != null) vals.add(itr);
                if (bank != null) vals.add(bank);
                if (vals.isEmpty()) yield null;
                BigDecimal min = vals.get(0);
                for (BigDecimal v : vals) {
                    if (v.compareTo(min) < 0) min = v;
                }
                yield min;
            }
            default -> triangulated;
        };
    }

    private CiLimitMethodResult bankingLimit(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal multiplier = DecisionValueHelper.bd(params.getOrDefault("bankingMultiplier", 3.0));
        String metric = String.valueOf(params.getOrDefault("bankingMetric", "banking.avg_daily_balance_3m"));
        BigDecimal base = DecisionValueHelper.num(input.metrics(), metric);
        if (base == null && "banking.avg_daily_balance_3m".equals(metric)) {
            base = DecisionValueHelper.num(input.metrics(), "bank.abb.average");
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("bankingMultiplier", multiplier);
        inputs.put("bankingMetric", metric);
        inputs.put("base", base);
        if (base == null) {
            return CiLimitMethodResult.builder()
                    .methodCode("BANKING_CREDIT_LIMIT")
                    .eligibleAmount(null)
                    .formulaVersion("BANKING_MULT_V1")
                    .dataStatus(DATA_INSUFFICIENT)
                    .inputs(inputs)
                    .evidenceRefs(List.of(Map.of("kind", "METRIC", "reference", metric)))
                    .detail(Map.of("reason", "BANKING_METRIC_MISSING"))
                    .build();
        }
        BigDecimal eligible = base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        return CiLimitMethodResult.builder()
                .methodCode("BANKING_CREDIT_LIMIT")
                .eligibleAmount(eligible)
                .formulaVersion("BANKING_MULT_V1")
                .dataStatus(DATA_AVAILABLE)
                .inputs(inputs)
                .evidenceRefs(List.of(Map.of("kind", "METRIC", "reference", metric)))
                .detail(Map.of("formula", "multiplier * bankingMetric"))
                .build();
    }

    private CiLimitMethodResult cashFlowLimit(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal cashFlow = DecisionValueHelper.num(input.metrics(), "cashflow.eligible_monthly");
        if (cashFlow == null) {
            cashFlow = DecisionValueHelper.num(input.metrics(), "income.eligible_monthly");
        }
        BigDecimal months = DecisionValueHelper.bd(params.getOrDefault("cashFlowMonths", 12));
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("cashFlow", cashFlow);
        inputs.put("cashFlowMonths", months);
        if (cashFlow == null) {
            return di("CASH_FLOW_LIMIT", "CASH_FLOW_V1", inputs, "cashflow.eligible_monthly");
        }
        BigDecimal eligible = cashFlow.multiply(months).setScale(2, RoundingMode.HALF_UP);
        return ok("CASH_FLOW_LIMIT", eligible, "CASH_FLOW_V1", inputs, "cashflow.eligible_monthly");
    }

    private CiLimitMethodResult foirLimit(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal income = DecisionValueHelper.num(input.metrics(), "income.eligible_monthly");
        BigDecimal existing = DecisionValueHelper.numOrZero(input.metrics(), "obligation.total_emi");
        if (!DecisionValueHelper.has(input.metrics(), "obligation.total_emi")) {
            existing = DecisionValueHelper.numOrZero(input.metrics(), "bureau.emi.monthly")
                    .max(DecisionValueHelper.numOrZero(input.metrics(), "bank.emi.monthly"));
        }
        BigDecimal allowedFoir = DecisionValueHelper.bd(params.getOrDefault("allowedFoir", 0.50));
        BigDecimal rate = DecisionValueHelper.bd(params.getOrDefault("interestRateForFoirSizing", 0.14));
        int tenure = input.requestedTenureMonths() != null ? input.requestedTenureMonths()
                : DecisionValueHelper.bd(params.getOrDefault("defaultTenureMonths", 18)).intValue();
        String amort = String.valueOf(params.getOrDefault("amortizationVersion", "EMI_FLAT_V1"));

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("income", income);
        inputs.put("existingObligations", existing);
        inputs.put("allowedFoir", allowedFoir);
        inputs.put("interestRate", rate);
        inputs.put("tenureMonths", tenure);
        inputs.put("amortizationVersion", amort);

        if (income == null || income.signum() <= 0) {
            return di("FOIR_LIMIT", amort, inputs, "income.eligible_monthly");
        }

        BigDecimal maxObligation = income.multiply(allowedFoir).setScale(2, RoundingMode.HALF_UP);
        BigDecimal availableEmi = maxObligation.subtract(existing).setScale(2, RoundingMode.HALF_UP);
        inputs.put("maxObligation", maxObligation);
        inputs.put("availableEmi", availableEmi);

        if (availableEmi.signum() <= 0) {
            return CiLimitMethodResult.builder()
                    .methodCode("FOIR_LIMIT")
                    .eligibleAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .formulaVersion(amort)
                    .dataStatus(DATA_AVAILABLE)
                    .inputs(inputs)
                    .evidenceRefs(List.of(
                            Map.of("kind", "METRIC", "reference", "income.eligible_monthly"),
                            Map.of("kind", "METRIC", "reference", "obligation.total_emi")))
                    .detail(Map.of("reason", "NO_REMAINING_EMI_CAPACITY"))
                    .build();
        }

        BigDecimal principal = principalFromEmi(availableEmi, rate, tenure, amort);
        inputs.put("principal", principal);
        return CiLimitMethodResult.builder()
                .methodCode("FOIR_LIMIT")
                .eligibleAmount(principal)
                .formulaVersion(amort)
                .dataStatus(DATA_AVAILABLE)
                .inputs(inputs)
                .evidenceRefs(List.of(
                        Map.of("kind", "METRIC", "reference", "income.eligible_monthly"),
                        Map.of("kind", "METRIC", "reference", "obligation.total_emi")))
                .detail(Map.of("formula", "principalFromEmi(availableEmi, rate, tenure)"))
                .build();
    }

    /**
     * EMI_FLAT_V1: EMI = P*(1+r*t)/n  →  P = EMI * n / (1 + r*t) where t=tenure/12, n=tenure months.
     * EMI_REDUCING_V1: standard annuity P = EMI * (1-(1+r)^-n)/r with monthly r.
     */
    public BigDecimal principalFromEmi(BigDecimal emi, BigDecimal annualRate, int tenureMonths, String amortVersion) {
        if (emi == null || tenureMonths <= 0) {
            return BigDecimal.ZERO;
        }
        String ver = amortVersion == null ? "EMI_FLAT_V1" : amortVersion.toUpperCase(Locale.ROOT);
        if ("EMI_REDUCING_V1".equals(ver)) {
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            if (monthlyRate.signum() == 0) {
                return emi.multiply(BigDecimal.valueOf(tenureMonths)).setScale(2, RoundingMode.HALF_UP);
            }
            // P = EMI * (1 - (1+r)^-n) / r
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal pow = onePlusR.pow(tenureMonths, MathContext.DECIMAL64);
            BigDecimal factor = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(pow, 12, RoundingMode.HALF_UP));
            return emi.multiply(factor).divide(monthlyRate, 2, RoundingMode.HALF_UP);
        }
        // EMI_FLAT_V1
        BigDecimal tYears = BigDecimal.valueOf(tenureMonths).divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        BigDecimal denom = BigDecimal.ONE.add(annualRate.multiply(tYears));
        return emi.multiply(BigDecimal.valueOf(tenureMonths)).divide(denom, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal emiFromPrincipal(BigDecimal principal, BigDecimal annualRate, int tenureMonths, String amortVersion) {
        if (principal == null || tenureMonths <= 0) {
            return BigDecimal.ZERO;
        }
        String ver = amortVersion == null ? "EMI_FLAT_V1" : amortVersion.toUpperCase(Locale.ROOT);
        if ("EMI_REDUCING_V1".equals(ver)) {
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            if (monthlyRate.signum() == 0) {
                return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
            }
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal pow = onePlusR.pow(tenureMonths, MathContext.DECIMAL64);
            BigDecimal numer = principal.multiply(monthlyRate).multiply(pow);
            BigDecimal denom = pow.subtract(BigDecimal.ONE);
            return numer.divide(denom, 2, RoundingMode.HALF_UP);
        }
        BigDecimal tYears = BigDecimal.valueOf(tenureMonths).divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        BigDecimal total = principal.multiply(BigDecimal.ONE.add(annualRate.multiply(tYears)));
        return total.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
    }

    private CiLimitMethodResult dscrLimit(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal cashFlow = DecisionValueHelper.num(input.metrics(), "cashflow.eligible_annual");
        if (cashFlow == null) {
            cashFlow = DecisionValueHelper.num(input.metrics(), "cashflow.eligible_monthly");
            if (cashFlow != null) {
                cashFlow = cashFlow.multiply(BigDecimal.valueOf(12));
            }
        }
        BigDecimal minDscr = DecisionValueHelper.bd(params.getOrDefault("minDscr", 1.25));
        BigDecimal rate = DecisionValueHelper.bd(params.getOrDefault("interestRateForFoirSizing", 0.14));
        int tenure = input.requestedTenureMonths() != null ? input.requestedTenureMonths() : 18;
        String amort = String.valueOf(params.getOrDefault("amortizationVersion", "EMI_FLAT_V1"));

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("cashFlow", cashFlow);
        inputs.put("minDscr", minDscr);
        if (cashFlow == null) {
            return di("DSCR_LIMIT", "DSCR_V1", inputs, "cashflow.eligible_annual");
        }
        BigDecimal maxDebtService = cashFlow.divide(minDscr, 2, RoundingMode.HALF_UP);
        BigDecimal monthly = maxDebtService.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal principal = principalFromEmi(monthly, rate, tenure, amort);
        inputs.put("maxDebtService", maxDebtService);
        inputs.put("monthlyDebtService", monthly);
        return ok("DSCR_LIMIT", principal, "DSCR_V1", inputs, "cashflow.eligible_annual");
    }

    private CiLimitMethodResult collateralLimit(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal collateral = DecisionValueHelper.num(input.metrics(), "collateral.value");
        BigDecimal maxLtv = DecisionValueHelper.bd(params.getOrDefault("maxLtv", 0.75));
        BigDecimal haircut = DecisionValueHelper.bd(params.getOrDefault("haircut", 0.10));
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("collateralValue", collateral);
        inputs.put("maxLtv", maxLtv);
        inputs.put("haircut", haircut);
        if (collateral == null) {
            return di("COLLATERAL_LIMIT", "COLLATERAL_LTV_V1", inputs, "collateral.value");
        }
        BigDecimal effective = collateral.multiply(BigDecimal.ONE.subtract(haircut));
        BigDecimal eligible = effective.multiply(maxLtv).setScale(2, RoundingMode.HALF_UP);
        inputs.put("effectiveCollateral", effective);
        return ok("COLLATERAL_LIMIT", eligible, "COLLATERAL_LTV_V1", inputs, "collateral.value");
    }

    private CiLimitMethodResult invoiceLimit(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal invoices = DecisionValueHelper.num(input.metrics(), "invoice.eligible_receivables");
        BigDecimal advancePct = DecisionValueHelper.bd(params.getOrDefault("invoiceAdvancePct", 0.80));
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("invoices", invoices);
        inputs.put("invoiceAdvancePct", advancePct);
        if (invoices == null) {
            return di("INVOICE_FINANCE_LIMIT", "INVOICE_V1", inputs, "invoice.eligible_receivables");
        }
        return ok("INVOICE_FINANCE_LIMIT",
                invoices.multiply(advancePct).setScale(2, RoundingMode.HALF_UP),
                "INVOICE_V1", inputs, "invoice.eligible_receivables");
    }

    private CiLimitMethodResult policyCap(Map<String, Object> params, DecisionRuntimeInput input) {
        BigDecimal cap = DecisionValueHelper.bd(params.get("policyCap"));
        if (cap == null) {
            cap = DecisionValueHelper.bd(input.policyParameters().get("POLICY_CAP"));
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("policyCap", cap);
        if (cap == null) {
            return di("POLICY_CAP", "POLICY_CAP_V1", inputs, "POLICY_CAP");
        }
        return ok("POLICY_CAP", cap.setScale(2, RoundingMode.HALF_UP), "POLICY_CAP_V1", inputs, "POLICY_CAP");
    }

    private CiLimitMethodResult requestedAmount(DecisionRuntimeInput input) {
        BigDecimal req = input.requestedAmount();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("requestedAmount", req);
        if (req == null) {
            return di("REQUESTED_AMOUNT", "REQUESTED_V1", inputs, "requestedAmount");
        }
        return ok("REQUESTED_AMOUNT", req.setScale(2, RoundingMode.HALF_UP), "REQUESTED_V1", inputs, "requestedAmount");
    }

    private CiLimitMethodResult di(String code, String formula, Map<String, Object> inputs, String ref) {
        return CiLimitMethodResult.builder()
                .methodCode(code)
                .eligibleAmount(null)
                .formulaVersion(formula)
                .dataStatus(DATA_INSUFFICIENT)
                .inputs(inputs)
                .evidenceRefs(List.of(Map.of("kind", "METRIC", "reference", ref)))
                .detail(Map.of("reason", "DATA_INSUFFICIENT"))
                .build();
    }

    private CiLimitMethodResult ok(String code, BigDecimal amount, String formula,
                                   Map<String, Object> inputs, String ref) {
        return CiLimitMethodResult.builder()
                .methodCode(code)
                .eligibleAmount(amount)
                .formulaVersion(formula)
                .dataStatus(DATA_AVAILABLE)
                .inputs(inputs)
                .evidenceRefs(List.of(Map.of("kind", "METRIC", "reference", ref)))
                .detail(Map.of())
                .build();
    }

    private LimitCombineStrategy parseCombine(Object raw) {
        if (raw == null) {
            return LimitCombineStrategy.MIN;
        }
        try {
            return LimitCombineStrategy.valueOf(String.valueOf(raw).toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return LimitCombineStrategy.MIN;
        }
    }

    private record CombineOutcome(String method, BigDecimal amount, String dataStatus, List<String> reasons) {}

    private CombineOutcome combine(List<CiLimitMethodResult> candidates, LimitCombineStrategy strategy,
                                   String primary, Map<String, Object> params) {
        List<CiLimitMethodResult> available = candidates.stream()
                .filter(c -> DATA_AVAILABLE.equals(c.getDataStatus()) && c.getEligibleAmount() != null)
                .toList();
        if (available.isEmpty()) {
            return new CombineOutcome(null, null, DATA_INSUFFICIENT, List.of("NO_AVAILABLE_LIMIT_METHODS"));
        }
        return switch (strategy) {
            case MAX -> {
                CiLimitMethodResult best = available.get(0);
                for (CiLimitMethodResult c : available) {
                    if (c.getEligibleAmount().compareTo(best.getEligibleAmount()) > 0) {
                        best = c;
                    }
                }
                yield new CombineOutcome(best.getMethodCode(), best.getEligibleAmount(), DATA_AVAILABLE, List.of("COMBINE_MAX"));
            }
            case SELECT_PRIMARY -> {
                String p = primary != null ? primary : available.get(0).getMethodCode();
                CiLimitMethodResult sel = available.stream()
                        .filter(c -> p.equals(c.getMethodCode()))
                        .findFirst()
                        .orElse(available.get(0));
                yield new CombineOutcome(sel.getMethodCode(), sel.getEligibleAmount(), DATA_AVAILABLE,
                        List.of("COMBINE_SELECT_PRIMARY"));
            }
            case WEIGHTED -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> weights = params.get("weights") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                BigDecimal sumW = BigDecimal.ZERO;
                BigDecimal sum = BigDecimal.ZERO;
                for (CiLimitMethodResult c : available) {
                    BigDecimal w = DecisionValueHelper.bd(weights.getOrDefault(c.getMethodCode(), 1));
                    if (w == null) w = BigDecimal.ONE;
                    sumW = sumW.add(w);
                    sum = sum.add(c.getEligibleAmount().multiply(w));
                }
                BigDecimal avg = sumW.signum() == 0 ? BigDecimal.ZERO
                        : sum.divide(sumW, 2, RoundingMode.HALF_UP);
                yield new CombineOutcome("WEIGHTED", avg, DATA_AVAILABLE, List.of("COMBINE_WEIGHTED"));
            }
            case CONDITIONAL -> {
                // Prefer FOIR if present, else MIN
                CiLimitMethodResult foir = available.stream()
                        .filter(c -> "FOIR_LIMIT".equals(c.getMethodCode()))
                        .findFirst().orElse(null);
                if (foir != null) {
                    yield new CombineOutcome(foir.getMethodCode(), foir.getEligibleAmount(), DATA_AVAILABLE,
                            List.of("COMBINE_CONDITIONAL_FOIR"));
                }
                yield combine(available, LimitCombineStrategy.MIN, primary, params);
            }
            case MIN -> {
                CiLimitMethodResult best = available.get(0);
                for (CiLimitMethodResult c : available) {
                    if (c.getEligibleAmount().compareTo(best.getEligibleAmount()) < 0) {
                        best = c;
                    }
                }
                yield new CombineOutcome(best.getMethodCode(), best.getEligibleAmount(), DATA_AVAILABLE, List.of("COMBINE_MIN"));
            }
        };
    }

    private Map<String, Object> toMap(CiLimitMethodResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method", r.getMethodCode());
        m.put("eligibleAmount", r.getEligibleAmount());
        m.put("inputs", r.getInputs());
        m.put("formulaVersion", r.getFormulaVersion());
        m.put("dataStatus", r.getDataStatus());
        m.put("evidenceRefs", r.getEvidenceRefs());
        m.put("detail", r.getDetail());
        return m;
    }
}
