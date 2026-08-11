package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyScopeUx2bTest {

    private PolicyApplicabilityResolver resolver;
    private PolicyLifecycleService lifecycle;
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
    void constrainedBorrowerType_missingAppAttribute_doesNotMatch() {
        PolicyApplicabilityRecord policy = scoped(
                List.of("BUSINESS_WC_INVOICE_DISCOUNTING"), "COMPANY", null, null, null);
        lifecycle.registerForTests(tenantId, policy);

        ApplicationPolicyQuery missingBorrower = new ApplicationPolicyQuery(
                "APP-1", "BUSINESS_WC_INVOICE_DISCOUNTING", null, null, null, null, null,
                new BigDecimal("2000000"), LocalDate.of(2026, 9, 1));

        Map<String, Object> result = resolver.resolve(missingBorrower, lifecycle.catalogueList(tenantId));
        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
    }

    @Test
    void amountBand_missingAppAmount_doesNotMatch() {
        PolicyApplicabilityRecord policy = scoped(
                List.of("DIGILEAP"), null, new BigDecimal("100000"), new BigDecimal("500000"), null);
        lifecycle.registerForTests(tenantId, policy);

        ApplicationPolicyQuery noAmount = new ApplicationPolicyQuery(
                "APP-1", "DIGILEAP", null, null, null, null, null,
                null, LocalDate.of(2026, 6, 1));

        assertThat(resolver.resolve(noAmount, lifecycle.catalogueList(tenantId)).get("outcome"))
                .isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
    }

    @Test
    void productMismatch_noMatch() {
        lifecycle.registerForTests(tenantId, scoped(List.of("BUSINESS_TERM_LOAN"), null, null, null, null));
        Map<String, Object> result = resolver.resolve(
                new ApplicationPolicyQuery("A", "BUSINESS_WC_INVOICE_DISCOUNTING", null, null, "COMPANY",
                        null, null, new BigDecimal("100000"), LocalDate.of(2026, 9, 1)),
                lifecycle.catalogueList(tenantId));
        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.NO_APPLICABLE_POLICY);
    }

    @Test
    void exactOne_stillWorks() {
        lifecycle.registerForTests(tenantId, scoped(
                List.of("BUSINESS_WC_INVOICE_DISCOUNTING"), "COMPANY",
                new BigDecimal("1000000"), new BigDecimal("5000000"), null));
        Map<String, Object> result = resolver.resolve(
                new ApplicationPolicyQuery("A", "BUSINESS_WC_INVOICE_DISCOUNTING", null, "BORROWER", "COMPANY",
                        null, null, new BigDecimal("2000000"), LocalDate.of(2026, 9, 1)),
                lifecycle.catalogueList(tenantId));
        assertThat(result.get("outcome")).isEqualTo(PolicyApplicabilityResolver.EXACTLY_ONE);
        assertThat(result.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void twoMatching_stillAmbiguous() {
        lifecycle.registerForTests(tenantId, scoped(List.of("DIGILEAP"), null, null, null, "v1"));
        lifecycle.registerForTests(tenantId, scoped(List.of("DIGILEAP"), null, null, null, "v2"));
        Map<String, Object> result = resolver.resolve(
                new ApplicationPolicyQuery("A", "DIGILEAP", null, null, null, null, null,
                        new BigDecimal("100000"), LocalDate.of(2026, 9, 20)),
                lifecycle.catalogueList(tenantId));
        assertThat(result.get("outcome"))
                .isEqualTo(PolicyApplicabilityResolver.AMBIGUOUS_POLICY_CONFIGURATION);
    }

    @Test
    void overlapDetect_distinctBorrowerTypes_notBlocked() {
        PolicyApplicabilityRecord company = scoped(
                List.of("BUSINESS_WC_INVOICE_DISCOUNTING"), "COMPANY", null, null, "v1");
        lifecycle.registerForTests(tenantId, company);
        PolicyApplicabilityRecord individual = scoped(
                List.of("BUSINESS_WC_INVOICE_DISCOUNTING"), "INDIVIDUAL", null, null, "v2");
        Map<String, Object> overlap = resolver.detectOverlap(individual, lifecycle.catalogueList(tenantId));
        assertThat(overlap.get("blocked")).isEqualTo(false);
    }

    @Test
    void overlapDetect_sameScope_blocked() {
        lifecycle.registerForTests(tenantId, scoped(
                List.of("BUSINESS_WC_INVOICE_DISCOUNTING"), "COMPANY", null, null, "v1"));
        Map<String, Object> overlap = resolver.detectOverlap(
                scoped(List.of("BUSINESS_WC_INVOICE_DISCOUNTING"), "COMPANY", null, null, "v2"),
                lifecycle.catalogueList(tenantId));
        assertThat(overlap.get("blocked")).isEqualTo(true);
    }

    @Test
    void scopeSummary_readable_noJson() {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("products", List.of("BUSINESS_WC_INVOICE_DISCOUNTING"));
        app.put("borrowerType", "COMPANY");
        app.put("minLoanAmount", "1000000");
        app.put("maxLoanAmount", "10000000");
        app.put("effectiveFrom", "2026-09-01");
        Map<String, Object> summary = PolicyScopeSupport.summarize(app);
        assertThat(String.valueOf(summary.get("appliesTo"))).contains("Invoice Discounting");
        assertThat(String.valueOf(summary.get("appliesTo"))).contains("Company");
        assertThat(String.valueOf(summary.get("appliesTo"))).contains("₹10,00,000");
        assertThat(String.valueOf(summary.get("appliesTo"))).contains("₹1,00,00,000");
        assertThat(String.valueOf(summary.get("appliesTo"))).doesNotContain("{");
        assertThat(String.valueOf(summary.get("effective"))).contains("2026-09-01");
        assertThat(summary.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void minGreaterThanMax_rejected() {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("minLoanAmount", "500000");
        app.put("maxLoanAmount", "100000");
        assertThatThrownBy(() -> PolicyScopeSupport.normalizeAndValidate(app))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum");
    }

    @Test
    void customerSegmentSemantics_areIntakeRelationship() {
        Map<String, Object> opts = PolicyScopeSupport.scopeOptions();
        assertThat(String.valueOf(opts.get("customerSegmentFieldMeaning"))).contains("IntakeSegment");
        assertThat(String.valueOf(opts.get("customerSegmentFieldMeaning"))).doesNotContain("MSME");
        @SuppressWarnings("unchecked")
        Map<String, Object> advanced = (Map<String, Object>) opts.get("advanced");
        assertThat(advanced.get("programScheme")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) advanced.get("programScheme")).get("exposed")).isEqualTo(false);
    }

    @Test
    void saveDraft_incompleteScope_allowed_andDoesNotTouchRules() {
        PolicyStudioSession session = sessionWithOneRule();
        String ruleIdBefore = session.getRuleCandidates().get(0).getSystemRuleId();
        Object exprBefore = session.getRuleCandidates().get(0).getExpression();
        int ruleCount = session.getRuleCandidates().size();

        Map<String, Object> saved = lifecycle.saveDraft(session, Map.of(
                "products", List.of(),
                "borrowerType", "",
                "minLoanAmount", "",
                "maxLoanAmount", "",
                "reasonForChange", "incomplete scope draft"
        ));

        assertThat(saved.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(saved.get("rulesUnchanged")).isEqualTo(true);
        assertThat(session.getRuleCandidates()).hasSize(ruleCount);
        assertThat(session.getRuleCandidates().get(0).getSystemRuleId()).isEqualTo(ruleIdBefore);
        assertThat(session.getRuleCandidates().get(0).getExpression()).isEqualTo(exprBefore);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) saved.get("scopeSummary");
        assertThat(String.valueOf(summary.get("appliesTo"))).contains("All products");
    }

    @Test
    void productOnly_andProductBorrowerAmount_saveReload() {
        PolicyStudioSession session = sessionWithOneRule();
        lifecycle.saveDraft(session, Map.of(
                "products", List.of("BUSINESS_TERM_LOAN"),
                "effectiveFrom", "2026-09-01"
        ));
        Map<String, Object> viewA = lifecycle.settingsView(session);
        assertThat(String.valueOf(((Map<?, ?>) viewA.get("scopeSummary")).get("appliesTo")))
                .contains("Business Term Loan");

        lifecycle.saveDraft(session, Map.of(
                "products", List.of("BUSINESS_WC_INVOICE_DISCOUNTING"),
                "borrowerType", "COMPANY",
                "minLoanAmount", "1000000",
                "maxLoanAmount", "5000000",
                "customerSegment", "BORROWER",
                "effectiveFrom", "2026-09-01"
        ));
        Map<String, Object> viewB = lifecycle.settingsView(session);
        String applies = String.valueOf(((Map<?, ?>) viewB.get("scopeSummary")).get("appliesTo"));
        assertThat(applies).contains("Invoice Discounting");
        assertThat(applies).contains("Company");
        // rules untouched
        assertThat(session.getRuleCandidates()).hasSize(1);
    }

    @Test
    void mappingReliability_programNotReliable() {
        Map<String, Object> map = PolicyScopeSupport.mappingReliability();
        assertThat(((Map<?, ?>) map.get("PROGRAM_SCHEME")).get("reliable")).isEqualTo(false);
        assertThat(((Map<?, ?>) map.get("PRODUCT")).get("reliable")).isEqualTo(true);
        assertThat(((Map<?, ?>) map.get("BORROWER_TYPE")).get("reliable")).isEqualTo(true);
        assertThat(map.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    private PolicyApplicabilityRecord scoped(
            List<String> products, String borrower, BigDecimal min, BigDecimal max, String version) {
        return new PolicyApplicabilityRecord(
                UUID.randomUUID(),
                "Scope Test Policy",
                version == null ? "v1" : version,
                "CREDIT_POLICY",
                PolicyBusinessLifecycleStatus.ACTIVE,
                products,
                null, null, borrower, null, null, null,
                min, max,
                LocalDate.of(2026, 9, 1), null,
                null, null, "cm", "checker", "author",
                false,
                Map.of());
    }

    private PolicyStudioSession sessionWithOneRule() {
        UUID docId = UUID.randomUUID();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(docId)
                .tenantId(tenantId)
                .name("SCF Scope Draft")
                .contentHash("scope-ux-2b-" + docId)
                .metadata(new LinkedHashMap<>())
                .build();
        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(doc);
        CiPolicyRuleCandidate rule = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("SCOPE_TEST_RULE")
                .expression(Map.of("op", "LT", "left", "BUREAU_SCORE", "right", 700))
                .metadata(new LinkedHashMap<>(Map.of("note", "Minimum bureau score 700")))
                .build();
        session.getRuleCandidates().add(rule);
        return session;
    }
}
