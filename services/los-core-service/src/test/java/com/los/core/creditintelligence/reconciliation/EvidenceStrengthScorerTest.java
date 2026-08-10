package com.los.core.creditintelligence.reconciliation;

import com.los.core.creditintelligence.reconciliation.domain.EvidenceStrengthGrade;
import com.los.core.creditintelligence.reconciliation.domain.TriangulationStatus;
import com.los.core.creditintelligence.reconciliation.service.EvidenceStrengthScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceStrengthScorerTest {

    private EvidenceStrengthScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new EvidenceStrengthScorer();
    }

    @Test
    void allSourcesStrong() {
        var r = scorer.score(new EvidenceStrengthScorer.StrengthInput(
                true, true, true, true, true, true,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                TriangulationStatus.STRONG_ALIGNMENT, 0, 0));
        assertThat(r.score().doubleValue()).isGreaterThanOrEqualTo(80);
        assertThat(r.grade()).isEqualTo(EvidenceStrengthGrade.STRONG);
        assertThat(r.components().get("note")).isEqualTo("NOT_A_CREDIT_OR_RISK_SCORE");
    }

    @Test
    void missingAisOnlyStillAdequate() {
        var r = scorer.score(new EvidenceStrengthScorer.StrengthInput(
                true, true, true, true, false, true,
                BigDecimal.valueOf(0.9), BigDecimal.valueOf(0.9), BigDecimal.ONE, BigDecimal.valueOf(0.9),
                TriangulationStatus.REASONABLE_ALIGNMENT, 0, 1));
        assertThat(r.grade()).isIn(EvidenceStrengthGrade.STRONG, EvidenceStrengthGrade.ADEQUATE);
    }

    @Test
    void missingBankingWeakens() {
        var r = scorer.score(new EvidenceStrengthScorer.StrengthInput(
                true, true, false, true, false, false,
                BigDecimal.valueOf(0.7), BigDecimal.valueOf(0.5), BigDecimal.ONE, BigDecimal.valueOf(0.5),
                TriangulationStatus.DATA_INSUFFICIENT, 0, 2));
        assertThat(r.score().doubleValue()).isLessThan(80);
    }

    @Test
    void severalConflictsInsufficientOrWeak() {
        var r = scorer.score(new EvidenceStrengthScorer.StrengthInput(
                true, true, true, true, false, false,
                BigDecimal.valueOf(0.8), BigDecimal.valueOf(0.8), BigDecimal.ONE, BigDecimal.valueOf(0.8),
                TriangulationStatus.MATERIAL_CONFLICT, 4, 2));
        assertThat(r.grade()).isIn(EvidenceStrengthGrade.WEAK, EvidenceStrengthGrade.INSUFFICIENT);
    }

    @Test
    void notConfusedWithCreditScore() {
        var r = scorer.score(new EvidenceStrengthScorer.StrengthInput(
                true, true, true, true, true, true,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                TriangulationStatus.STRONG_ALIGNMENT, 0, 0));
        assertThat(r.methodVersion()).isEqualTo("EVIDENCE_STRENGTH_V1");
        assertThat(r.components()).containsKey("note");
    }
}
