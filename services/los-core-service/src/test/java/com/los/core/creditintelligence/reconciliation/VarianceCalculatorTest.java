package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.reconciliation.domain.VarianceMethod;
import com.los.core.creditintelligence.reconciliation.service.VarianceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VarianceCalculatorTest {

    private VarianceCalculator calc;

    @BeforeEach
    void setUp() {
        calc = new VarianceCalculator();
    }

    @Test
    void absolute() {
        var r = calc.calculate(bd("100"), bd("80"), VarianceMethod.ABSOLUTE);
        assertThat(r.absoluteVariance()).isEqualByComparingTo("20.00");
        assertThat(r.percentageVariance()).isNull();
    }

    @Test
    void percentOfRightAvoidsDivByZero() {
        var r = calc.calculate(bd("50"), BigDecimal.ZERO, VarianceMethod.PERCENT_OF_RIGHT);
        assertThat(r.percentageVariance()).isEqualByComparingTo("5000.0000");
    }

    @Test
    void percentOfMax() {
        var r = calc.calculate(bd("100"), bd("80"), VarianceMethod.PERCENT_OF_MAX);
        // |20|/100*100 = 20
        assertThat(r.percentageVariance()).isEqualByComparingTo("20.0000");
    }

    @Test
    void symmetricPercentDifference() {
        var r = calc.calculate(bd("84000000"), bd("81000000"), VarianceMethod.SYMMETRIC_PERCENT_DIFFERENCE);
        // |3e6| / ((84+81)/2 e6) * 100 ≈ 3.636
        assertThat(r.percentageVariance().doubleValue()).isBetween(3.5, 3.8);
    }

    @Test
    void ratio() {
        var r = calc.calculate(bd("110"), bd("100"), VarianceMethod.RATIO);
        assertThat(r.ratio()).isEqualByComparingTo("1.10000000");
        assertThat(r.percentageVariance()).isEqualByComparingTo("10.0000");
    }

    @Test
    void percentOfAverage() {
        var r = calc.calculate(bd("100"), bd("50"), VarianceMethod.PERCENT_OF_AVERAGE);
        // abs 50 / avg 75 * 100
        assertThat(r.percentageVariance().doubleValue()).isBetween(66.0, 67.0);
    }

    @Test
    void percentOfLeft() {
        var r = calc.calculate(bd("100"), bd("90"), VarianceMethod.PERCENT_OF_LEFT);
        assertThat(r.percentageVariance()).isEqualByComparingTo("10.0000");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
