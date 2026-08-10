package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyApplicabilityResolverTest {

    private PolicyApplicabilityResolver resolver;
    private UUID tenantId;
    private PolicyLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        resolver = new PolicyApplicabilityResolver();
        tenantId = UUID.randomUUID();
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCutover().setAllowCanonicalAuthority(false);
        lifecycle = new PolicyLifecycleService(resolver, null, props, null);
        lifecycle.clearCatalogueForTests(tenantId);
    }

    @Test
    void exactMatch_returnsExactlyOne() {
        PolicyApplicabilityRecord v2 = digileap("v2", "ACTIVE",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 8, 31));
        lifecycle.registerForTests(tenantId, v2);

        Map<String, Object> result = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 8, 25), null),
                lifecycle.catalogueList(tenantId));

        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(result.get("onePolicySelected")).isEqualTo(true);
        assertThat(result.get("allowCanonicalAuthority")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> selected = (Map<String, Object>) result.get("selectedPolicy");
        assertThat(selected.get("policyVersion")).isEqualTo("v2");
        assertThat(String.valueOf(result.get("reason"))).contains("ONE POLICY SELECTED");
    }

    @Test
    void noPolicy_whenProductMismatch() {
        lifecycle.registerForTests(tenantId, digileap("v1", "ACTIVE",
                LocalDate.of(2026, 1, 1), null));

        Map<String, Object> result = resolver.resolve(
                query("APP-Y", "SMART_SWITCH", LocalDate.of(2026, 8, 25), null),
                lifecycle.catalogueList(tenantId));

        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
        assertThat(result.get("selectedPolicy")).isNull();
    }

    @Test
    void futureScheduled_notSelectedBeforeEffective() {
        lifecycle.registerForTests(tenantId, digileap("v2", "SCHEDULED",
                LocalDate.of(2026, 9, 1), null));

        Map<String, Object> result = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 8, 25), null),
                lifecycle.catalogueList(tenantId));

        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
    }

    @Test
    void expiredPolicy_notSelected() {
        lifecycle.registerForTests(tenantId, digileap("v1", "ACTIVE",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 8, 31)));

        Map<String, Object> result = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 9, 3), null),
                lifecycle.catalogueList(tenantId));

        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
    }

    @Test
    void effectiveDateTransition_v1ThenV2() {
        lifecycle.registerForTests(tenantId, digileap("v1", "ACTIVE",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 8, 31)));
        lifecycle.registerForTests(tenantId, digileap("v2", "ACTIVE",
                LocalDate.of(2026, 9, 1), null));

        Map<String, Object> august = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 8, 25), null),
                lifecycle.catalogueList(tenantId));
        Map<String, Object> september = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 9, 3), null),
                lifecycle.catalogueList(tenantId));

        assertThat(august.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(((Map<?, ?>) august.get("selectedPolicy")).get("policyVersion")).isEqualTo("v1");
        assertThat(september.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(((Map<?, ?>) september.get("selectedPolicy")).get("policyVersion")).isEqualTo("v2");
    }

    @Test
    void overlappingPolicies_ambiguousConfiguration() {
        lifecycle.registerForTests(tenantId, digileap("v3", "ACTIVE",
                LocalDate.of(2026, 9, 1), null));
        lifecycle.registerForTests(tenantId, digileap("v4", "SCHEDULED",
                LocalDate.of(2026, 9, 15), null));

        Map<String, Object> result = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 9, 20), null),
                lifecycle.catalogueList(tenantId));

        assertThat(result.get("outcome"))
                .isEqualTo(PolicyApplicabilityResolver.AMBIGUOUS_POLICY_CONFIGURATION);
        assertThat(result.get("configurationError")).isEqualTo(true);
        assertThat(result.get("selectedPolicy")).isNull();
        assertThat(result.get("onePolicySelected")).isEqualTo(false);
    }

    @Test
    void overlapDetect_blocksSchedule() {
        PolicyApplicabilityRecord existing = digileap("v3", "ACTIVE",
                LocalDate.of(2026, 9, 1), null);
        lifecycle.registerForTests(tenantId, existing);

        PolicyApplicabilityRecord candidate = digileap("v4", "SCHEDULED",
                LocalDate.of(2026, 9, 15), null);
        Map<String, Object> overlap = resolver.detectOverlap(candidate, lifecycle.catalogueList(tenantId));

        assertThat(overlap.get("blocked")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> conflict = (Map<String, Object>) ((List<?>) overlap.get("conflicts")).get(0);
        assertThat(String.valueOf(conflict.get("message"))).contains("DIGILEAP");
        assertThat(String.valueOf(conflict.get("message"))).contains("2026-09-15");
    }

    @Test
    void amountBandMismatch_noMatch() {
        PolicyApplicabilityRecord banded = new PolicyApplicabilityRecord(
                UUID.randomUUID(), "DigiLeap Policy", "v1", "BANKING_POLICY",
                PolicyBusinessLifecycleStatus.ACTIVE, List.of("DIGILEAP"),
                null, null, null, null, null,
                new BigDecimal("100000"), new BigDecimal("500000"),
                LocalDate.of(2026, 1, 1), null,
                null, null, "cm", "checker", "author", false, Map.of());
        lifecycle.registerForTests(tenantId, banded);

        Map<String, Object> result = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 6, 1), new BigDecimal("50000")),
                lifecycle.catalogueList(tenantId));

        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
    }

    @Test
    void amountBandMatch_selects() {
        PolicyApplicabilityRecord banded = new PolicyApplicabilityRecord(
                UUID.randomUUID(), "DigiLeap Policy", "v1", "BANKING_POLICY",
                PolicyBusinessLifecycleStatus.ACTIVE, List.of("DIGILEAP"),
                null, null, null, null, null,
                new BigDecimal("100000"), new BigDecimal("500000"),
                LocalDate.of(2026, 1, 1), null,
                null, null, "cm", "checker", "author", false, Map.of());
        lifecycle.registerForTests(tenantId, banded);

        Map<String, Object> result = resolver.resolve(
                query("APP-X", "DIGILEAP", LocalDate.of(2026, 6, 1), new BigDecimal("250000")),
                lifecycle.catalogueList(tenantId));

        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
    }

    @Test
    void historicalReplay_retainsPinnedPolicy() {
        UUID evalId = UUID.randomUUID();
        UUID pinned = UUID.randomUUID();
        UUID newlyActive = UUID.randomUUID();
        lifecycle.pinHistoricalEvaluation(evalId, pinned, "v1", LocalDate.of(2026, 8, 25));
        Map<String, Object> replay = lifecycle.replayHistorical(evalId, newlyActive);

        assertThat(replay.get("usesPinnedVersion")).isEqualTo(true);
        assertThat(replay.get("changedByNewActivePolicy")).isEqualTo(false);
        assertThat(replay.get("pinnedPolicyVersionId")).isEqualTo(pinned.toString());
        assertThat(replay.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void productionAuthorityRemainsDisabled() {
        Map<String, Object> mapping = lifecycle.statusMapping();
        assertThat(mapping.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(mapping.get("productionAuthority")).isEqualTo("DISABLED");
    }

    @Test
    void shadowResolve_onePolicySelected() {
        lifecycle.registerForTests(tenantId, digileap("v2", "ACTIVE",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 8, 31)));
        Map<String, Object> result = lifecycle.resolveShadowApplication(tenantId, Map.of(
                "applicationCode", "APP-X",
                "productCode", "DIGILEAP",
                "evaluationDate", "2026-08-25"));
        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(result.get("banner")).isEqualTo("ONE POLICY SELECTED");
        assertThat(result.get("mode")).isEqualTo("SHADOW");
        assertThat(result.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    private PolicyApplicabilityRecord digileap(String version, String status, LocalDate from, LocalDate until) {
        return new PolicyApplicabilityRecord(
                UUID.randomUUID(),
                "DigiLeap Policy",
                version,
                "BANKING_POLICY",
                status,
                List.of("DIGILEAP"),
                null, null, null, null, null,
                null, null,
                from, until,
                null, null,
                "credit_manager", "policy_checker", "author",
                false,
                Map.of());
    }

    private ApplicationPolicyQuery query(String app, String product, LocalDate asOf, BigDecimal amount) {
        return new ApplicationPolicyQuery(app, product, null, null, null, null, null, amount, asOf);
    }
}
