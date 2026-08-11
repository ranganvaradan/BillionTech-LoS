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

/**
 * GOLDEN B / C — multi borrower type + ALL semantics for Policy Studio scope.
 */
class BorrowerTypeMultiSelectPolicyTest {

    private PolicyLifecycleService lifecycle;
    private PolicyApplicabilityResolver resolver;
    private UUID tenantId;

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
    void goldenB_companyAndLlp_matchOnlyThose() {
        PolicyApplicabilityRecord scoped = record(
                List.of("TERM_LOAN"), List.of("COMPANY", "LLP"), "v1");
        lifecycle.registerForTests(tenantId, scoped);

        assertThat(resolve("COMPANY").get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(resolve("LLP").get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(resolve("INDIVIDUAL").get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
        assertThat(resolve(null).get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
    }

    @Test
    void goldenC_allBorrowerTypes_matchesSupported() {
        PolicyApplicabilityRecord all = record(List.of("TERM_LOAN"), List.of(), "v2");
        lifecycle.registerForTests(tenantId, all);

        assertThat(resolve("COMPANY").get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(resolve("LLP").get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(resolve("INDIVIDUAL").get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(resolve("PROPRIETORSHIP").get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
    }

    @Test
    void versionClone_preservesBorrowerTypes_viaFromMap() {
        Map<String, Object> view = record(List.of("TERM_LOAN"), List.of("COMPANY", "LLP"), "v3")
                .toBusinessView();
        PolicyApplicabilityRecord reloaded = PolicyApplicabilityRecord.fromMap(view);
        assertThat(reloaded.borrowerTypes()).containsExactly("COMPANY", "LLP");
        assertThat(BorrowerTypeScope.matches(reloaded.borrowerTypes(), reloaded.borrowerType(), "COMPANY")).isTrue();
        assertThat(BorrowerTypeScope.matches(reloaded.borrowerTypes(), reloaded.borrowerType(), "INDIVIDUAL")).isFalse();
        // no accidental widening to ALL
        assertThat(BorrowerTypeScope.isAll(reloaded.borrowerTypes())).isFalse();
    }

    @Test
    void legacyScalar_stillReadable() {
        PolicyApplicabilityRecord legacy = new PolicyApplicabilityRecord(
                UUID.randomUUID(), "Legacy", "v1", "CREDIT_POLICY",
                PolicyBusinessLifecycleStatus.ACTIVE, List.of("TERM_LOAN"),
                null, null, "COMPANY", null, null, null,
                null, null,
                LocalDate.of(2026, 1, 1), null,
                null, null, "cm", "ck", "au", false, Map.of());
        assertThat(legacy.borrowerTypes()).containsExactly("COMPANY");
        assertThat(BorrowerTypeScope.matches(legacy.borrowerTypes(), legacy.borrowerType(), "COMPANY")).isTrue();
        assertThat(BorrowerTypeScope.matches(legacy.borrowerTypes(), legacy.borrowerType(), "LLP")).isFalse();
    }

    private Map<String, Object> resolve(String borrowerType) {
        return resolver.resolve(
                new ApplicationPolicyQuery(
                        "APP-X", "TERM_LOAN", null, null, borrowerType, null, null,
                        new BigDecimal("250000"), LocalDate.of(2026, 6, 1)),
                lifecycle.catalogueList(tenantId));
    }

    private static PolicyApplicabilityRecord record(List<String> products, List<String> borrowerTypes, String ver) {
        return new PolicyApplicabilityRecord(
                UUID.randomUUID(),
                "Multi BT Policy",
                ver,
                "CREDIT_POLICY",
                PolicyBusinessLifecycleStatus.ACTIVE,
                products,
                null, null, null, borrowerTypes, null, null,
                null, null,
                LocalDate.of(2026, 1, 1), null,
                null, null, "cm", "checker", "author",
                false,
                Map.of());
    }
}
