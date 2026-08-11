package com.los.core.creditintelligence.policystudio.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BorrowerTypeScopeTest {

    @Test
    void all_emptyOrExplicit_matchesAny() {
        assertThat(BorrowerTypeScope.matches(List.of(), null, "COMPANY")).isTrue();
        assertThat(BorrowerTypeScope.matches(List.of("ALL"), null, "LLP")).isTrue();
        assertThat(BorrowerTypeScope.matches(null, null, "INDIVIDUAL")).isTrue();
    }

    @Test
    void constrained_orMatch() {
        List<String> scope = List.of("COMPANY", "LLP");
        assertThat(BorrowerTypeScope.matches(scope, null, "COMPANY")).isTrue();
        assertThat(BorrowerTypeScope.matches(scope, null, "LLP")).isTrue();
        assertThat(BorrowerTypeScope.matches(scope, null, "INDIVIDUAL")).isFalse();
        assertThat(BorrowerTypeScope.matches(scope, null, null)).isFalse();
    }

    @Test
    void legacyScalar_readCompat() {
        assertThat(BorrowerTypeScope.matches(null, "COMPANY", "COMPANY")).isTrue();
        assertThat(BorrowerTypeScope.matches(null, "COMPANY", "LLP")).isFalse();
        assertThat(BorrowerTypeScope.normalize(List.of("COMPANY", "ALL"), null)).isEmpty();
    }

    @Test
    void noDuplicateMatch_listWinsOverLegacyWhenBothPresent() {
        // normalize prefers list; ALL token clears to empty
        assertThat(BorrowerTypeScope.normalize(List.of("COMPANY", "LLP"), "INDIVIDUAL"))
                .containsExactly("COMPANY", "LLP");
        assertThat(BorrowerTypeScope.matches(List.of("COMPANY", "LLP"), "INDIVIDUAL", "INDIVIDUAL"))
                .isFalse();
    }

    @Test
    void summaryLabel_natural() {
        assertThat(BorrowerTypeScope.summaryLabel(List.of())).isEqualTo("All borrower types");
        assertThat(BorrowerTypeScope.summaryLabel(List.of("COMPANY", "LLP"))).isEqualTo("COMPANY + LLP");
    }
}
