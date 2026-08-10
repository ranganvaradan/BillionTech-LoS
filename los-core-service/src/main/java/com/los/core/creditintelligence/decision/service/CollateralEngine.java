package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CollateralEngine {

    public record CollateralResult(
            boolean required,
            BigDecimal requiredCollateralValue,
            BigDecimal availableEligibleCollateral,
            BigDecimal effectiveCollateralValue,
            BigDecimal recommendedLtv,
            BigDecimal shortfall,
            boolean adequate,
            Map<String, Object> collateral,
            List<String> reasonCodes,
            Map<String, Object> detail
    ) {}

    public CollateralResult compute(Map<String, Object> collateralStrategy,
                                    DecisionRuntimeInput input,
                                    BigDecimal recommendedAmount) {
        Map<String, Object> s = collateralStrategy == null ? Map.of() : collateralStrategy;
        boolean required = Boolean.TRUE.equals(s.get("required"));
        BigDecimal maxLtv = DecisionValueHelper.bd(s.getOrDefault("maxLtv", 0.75));
        BigDecimal haircut = DecisionValueHelper.bd(s.getOrDefault("haircut", 0.10));

        BigDecimal available = DecisionValueHelper.num(input.metrics(), "collateral.value");
        List<String> reasons = new ArrayList<>();
        Map<String, Object> detail = new LinkedHashMap<>();

        if (!required && available == null) {
            Map<String, Object> coll = new LinkedHashMap<>();
            coll.put("required", false);
            coll.put("secured", false);
            reasons.add("UNSECURED_ALLOWED");
            detail.put("status", "UNSECURED");
            return new CollateralResult(false, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO,
                    true, coll, reasons, detail);
        }

        if (available == null) {
            reasons.add("COLLATERAL_VALUATION_MISSING");
            BigDecimal requiredValue = recommendedAmount == null || maxLtv.signum() == 0
                    ? null
                    : recommendedAmount.divide(maxLtv, 2, RoundingMode.HALF_UP);
            Map<String, Object> coll = new LinkedHashMap<>();
            coll.put("required", required);
            coll.put("shortfall", requiredValue);
            detail.put("status", "MISSING_VALUATION");
            return new CollateralResult(required, requiredValue, null, null, null, requiredValue,
                    !required, coll, reasons, detail);
        }

        BigDecimal effective = available.multiply(BigDecimal.ONE.subtract(haircut)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = recommendedAmount == null ? BigDecimal.ZERO : recommendedAmount;
        BigDecimal ltv = effective.signum() == 0 ? null
                : amount.divide(effective, 6, RoundingMode.HALF_UP);
        BigDecimal maxLoan = effective.multiply(maxLtv).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shortfall = amount.compareTo(maxLoan) > 0
                ? amount.subtract(maxLoan).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        boolean adequate = shortfall.signum() == 0;

        if (!adequate) {
            reasons.add("COLLATERAL_SHORTFALL");
        } else {
            reasons.add("COLLATERAL_ADEQUATE");
        }

        Map<String, Object> coll = new LinkedHashMap<>();
        coll.put("required", required);
        coll.put("available", available);
        coll.put("effective", effective);
        coll.put("maxLtv", maxLtv);
        coll.put("haircut", haircut);
        coll.put("shortfall", shortfall);

        detail.put("requiredCollateralValue", amount.signum() == 0 || maxLtv.signum() == 0
                ? null : amount.divide(maxLtv, 2, RoundingMode.HALF_UP));
        detail.put("availableEligibleCollateral", available);
        detail.put("effectiveCollateralValue", effective);
        detail.put("recommendedLtv", ltv);
        detail.put("shortfall", shortfall);

        return new CollateralResult(required,
                DecisionValueHelper.bd(detail.get("requiredCollateralValue")),
                available, effective, ltv, shortfall, adequate, coll, reasons, detail);
    }
}
