package com.los.core.creditintelligence.validation;

import com.los.core.creditintelligence.validation.domain.ObligationMatchStatus;
import com.los.core.creditintelligence.validation.service.LenderObligationMatcher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LenderObligationMatcherTest {

    private final LenderObligationMatcher matcher = new LenderObligationMatcher();

    @Test
    void hdfcAlias_matches() {
        UUID tenant = UUID.randomUUID();
        var match = matcher.match(
                tenant, UUID.randomUUID(),
                "HDFC BANK", "HDFC BANK LTD EMI NACH",
                BigDecimal.valueOf(182000), BigDecimal.valueOf(96000));
        assertThat(match.getMatchStatus()).isIn(
                ObligationMatchStatus.MATCH.name(), ObligationMatchStatus.PROBABLE_MATCH.name());
        assertThat(match.getPercentageVariance()).isNotNull();
        assertThat(match.getSignals()).isNotEmpty();
    }

    @Test
    void unrelatedLenders_noMatchOrAmbiguous() {
        UUID tenant = UUID.randomUUID();
        var match = matcher.match(
                tenant, UUID.randomUUID(),
                "HDFC BANK", "BAJAJ FINANCE",
                BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        assertThat(match.getMatchStatus()).isIn(
                ObligationMatchStatus.AMBIGUOUS.name(), ObligationMatchStatus.NO_MATCH.name());
    }
}
