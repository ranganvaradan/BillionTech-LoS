package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManualParameterOverridePolicyTest {

    @Test
    void bureauRecentInquiries90d_isOverridable() {
        assertThat(ManualParameterOverridePolicy.isOverridable("bureau.recent_inquiries_90d")).isTrue();
    }

    @Test
    void arbitraryBureauScore_isNotOverridable() {
        // Explicitly NOT scoped in — a blanket bureau score override was rejected by design.
        assertThat(ManualParameterOverridePolicy.isOverridable("bureau.score")).isFalse();
    }

    @Test
    void nullOrBlank_isNotOverridable() {
        assertThat(ManualParameterOverridePolicy.isOverridable(null)).isFalse();
        assertThat(ManualParameterOverridePolicy.isOverridable("")).isFalse();
        assertThat(ManualParameterOverridePolicy.isOverridable("   ")).isFalse();
    }
}
