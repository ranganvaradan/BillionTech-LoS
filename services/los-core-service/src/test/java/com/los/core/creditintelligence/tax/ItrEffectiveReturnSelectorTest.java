package com.los.core.creditintelligence.tax;

import com.los.core.creditintelligence.tax.domain.ReturnVersionType;
import com.los.core.creditintelligence.tax.domain.TaxConstants;
import com.los.core.creditintelligence.tax.util.ItrEffectiveReturnSelector;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItrEffectiveReturnSelectorTest {

    @Test
    void prefersLatestRevisedOverOriginal() {
        var original = new ItrEffectiveReturnSelector.Candidate(
                "2025-26", ReturnVersionType.ORIGINAL, LocalDate.of(2025, 7, 20), "o1", Map.of());
        var revised = new ItrEffectiveReturnSelector.Candidate(
                "2025-26", ReturnVersionType.REVISED, LocalDate.of(2025, 10, 1), "r1", Map.of());
        var result = ItrEffectiveReturnSelector.select(List.of(original, revised));
        assertThat(result.selectionVersion()).isEqualTo(TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1);
        assertThat(result.effective().identityKey()).isEqualTo("r1");
        assertThat(result.selectionBasis()).contains("REVISED");
        assertThat(result.lineage()).hasSize(2);
    }

    @Test
    void detectVersionType_fromHints() {
        assertThat(ItrEffectiveReturnSelector.detectVersionType("Revised u/s 139(5)"))
                .isEqualTo(ReturnVersionType.REVISED);
        assertThat(ItrEffectiveReturnSelector.detectVersionType("Original"))
                .isEqualTo(ReturnVersionType.ORIGINAL);
    }
}
