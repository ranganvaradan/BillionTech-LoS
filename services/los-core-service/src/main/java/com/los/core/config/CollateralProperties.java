package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "los.collateral")
public class CollateralProperties {

    private GoldCollateralProperties gold = new GoldCollateralProperties();

    @Data
    public static class GoldCollateralProperties {
        /** Fraction deducted from market value (e.g. 0.25 = 25% haircut). */
        private BigDecimal haircut = new BigDecimal("0.25");
        /** Maximum LTV percent allowed for gold loans (RBI guideline). */
        private BigDecimal maxLtv = new BigDecimal("75");
    }
}
