package com.los.core.service.underwriting;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorDueDiligenceServiceTest {

    @Test
    void strongAnswersYieldRatingA() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("externalCreditRating", "AA");
        answers.put("financialPerformance", "STRONG");
        answers.put("businessVintage", "GTE_10");
        answers.put("gstCompliance", "FULL");
        answers.put("industryRisk", "LOW");
        answers.put("adverseNewsFlow", "NONE");
        answers.put("legalLitigation", "NONE");
        answers.put("managementTrackRecord", "STRONG");

        Map<String, Object> block = AnchorDueDiligenceService.computeRatingBlock(answers, true);

        assertThat(block.get("creditRating")).isEqualTo("A");
        assertThat((Integer) block.get("score")).isGreaterThanOrEqualTo(80);
    }

    @Test
    void weakAnswersYieldRatingD() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("externalCreditRating", "D");
        answers.put("financialPerformance", "DISTRESSED");
        answers.put("businessVintage", "LT_3");
        answers.put("gstCompliance", "SIGNIFICANT_GAPS");
        answers.put("industryRisk", "HIGH");
        answers.put("adverseNewsFlow", "MATERIAL");
        answers.put("legalLitigation", "ONGOING_MATERIAL");
        answers.put("managementTrackRecord", "CONCERNS");

        Map<String, Object> block = AnchorDueDiligenceService.computeRatingBlock(answers, true);

        assertThat(block.get("creditRating")).isEqualTo("D");
        assertThat((Integer) block.get("score")).isLessThan(50);
    }
}
