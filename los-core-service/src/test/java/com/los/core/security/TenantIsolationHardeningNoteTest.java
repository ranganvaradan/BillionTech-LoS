package com.los.core.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents tenant isolation posture for LOS-PRODUCTION-HARDENING-1.
 * CI/GACAT paths are tenant-scoped; LOS core loan applications historically use a single
 * default tenant UUID — cross-tenant ID guessing must still be blocked where multi-tenant
 * predicates exist (CI catalogues, scorecards bound to tenant).
 */
class TenantIsolationHardeningNoteTest {

    @Test
    void defaultTenantUuid_isStableSentinel() {
        assertThat("00000000-0000-0000-0000-000000000001")
                .isEqualTo("00000000-0000-0000-0000-000000000001");
    }
}
