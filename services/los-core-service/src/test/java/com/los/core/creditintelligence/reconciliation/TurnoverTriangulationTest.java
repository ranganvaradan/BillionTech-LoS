package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.domain.TriangulationStatus;
import com.los.core.creditintelligence.reconciliation.service.TurnoverTriangulationService;
import com.los.core.creditintelligence.reconciliation.service.VarianceCalculator;
import com.los.core.creditintelligence.reconciliation.service.ReconciliationExplanationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TurnoverTriangulationTest {

    private TurnoverTriangulationService service;

    @BeforeEach
    void setUp() {
        service = new TurnoverTriangulationService(
                new VarianceCalculator(),
                new ReconciliationExplanationBuilder(),
                new CreditIntelligenceProperties());
    }

    @Test
    void allAlignedStrong() {
        var r = service.synthesize(input("84000000", "84000000", "84000000"));
        assertThat(r.status()).isEqualTo(TriangulationStatus.STRONG_ALIGNMENT);
        assertThat(r.gstItrPct().doubleValue()).isLessThan(1.0);
    }

    @Test
    void reasonableAlignment() {
        var r = service.synthesize(input("84000000", "81000000", "80000000"));
        assertThat(r.status()).isIn(
                TriangulationStatus.REASONABLE_ALIGNMENT,
                TriangulationStatus.STRONG_ALIGNMENT,
                TriangulationStatus.REVIEW_REQUIRED);
        assertThat(r.gstItrPct()).isNotNull();
        assertThat(r.gstBankPct()).isNotNull();
        assertThat(r.itrBankPct()).isNotNull();
    }

    @Test
    void oneOutlierReviewRequired() {
        var r = service.synthesize(input("84000000", "82000000", "50000000"));
        assertThat(r.status()).isIn(
                TriangulationStatus.REVIEW_REQUIRED,
                TriangulationStatus.MATERIAL_CONFLICT);
    }

    @Test
    void allMateriallyDifferent() {
        var r = service.synthesize(input("100000000", "50000000", "20000000"));
        assertThat(r.status()).isEqualTo(TriangulationStatus.MATERIAL_CONFLICT);
    }

    @Test
    void oneSourceMissing() {
        var r = service.synthesize(new TurnoverTriangulationService.TriangulationInput(
                new BigDecimal("84000000"), new BigDecimal("81000000"), null,
                BigDecimal.ONE, BigDecimal.ONE, null));
        assertThat(r.status()).isNotEqualTo(TriangulationStatus.DATA_INSUFFICIENT);
        assertThat(r.gstBankPct()).isNull();
    }

    @Test
    void fewerThanTwoSourcesDi() {
        var r = service.synthesize(new TurnoverTriangulationService.TriangulationInput(
                new BigDecimal("84000000"), null, null,
                BigDecimal.ONE, null, null));
        assertThat(r.status()).isEqualTo(TriangulationStatus.DATA_INSUFFICIENT);
    }

    private TurnoverTriangulationService.TriangulationInput input(String gst, String itr, String bank) {
        return new TurnoverTriangulationService.TriangulationInput(
                new BigDecimal(gst), new BigDecimal(itr), new BigDecimal(bank),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }
}
