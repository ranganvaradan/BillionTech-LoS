package com.los.core.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Gold collateral valuation breakdown using live or simulated bullion rates")
public record GoldValuationResult(
        BigDecimal weightGrams,
        BigDecimal purityPercent,
        BigDecimal liveRatePerGram,
        BigDecimal pureGoldWeight,
        BigDecimal marketValue,
        BigDecimal haircut,
        BigDecimal acceptedValue,
        BigDecimal maxLtvPercent,
        Instant rateTimestamp
) {
}
