package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreditCapabilityCatalogueServiceTest {

    private CreditCapabilityCatalogueService service;

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCutover().setAllowCanonicalAuthority(false);
        service = new CreditCapabilityCatalogueService(props);
    }

    @Test
    void catalogueContainsExpectedCapabilityIds() {
        Set<String> ids = new HashSet<>();
        service.listCapabilities().forEach(c -> ids.add(c.businessCapabilityId()));

        assertThat(ids).contains(
                "BUREAU.MIN_SCORE",
                "BUREAU.LIVE_UNSECURED_MAX",
                "BUREAU.ENQUIRIES_MAX",
                "BANK.CHEQUE_BOUNCE_MAX",
                "BANK.TURNOVER_PCT_GST_MIN",
                "BANK.ABB_MIN",
                "FIN.FOIR_MAX",
                "FIN.DSCR_MIN",
                "FIN.INTEREST_COVERAGE_MIN",
                "FIN.DEBT_EQUITY_MAX",
                "FIN.TOL_TNW_MAX",
                "GST.TURNOVER_MIN",
                "ELIG.BUSINESS_VINTAGE_MIN",
                "ELIG.REQUIRE_KYC_PASS",
                "LIMIT.ABS_CAP");
    }

    @Test
    void noDuplicateBusinessCapabilityIds() {
        List<String> ids = service.listCapabilities().stream()
                .map(BusinessCapability::businessCapabilityId)
                .toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void bureauMinScore_exposesMinimumScoreParameter() {
        BusinessCapability cap = service.findById("BUREAU.MIN_SCORE").orElseThrow();
        assertThat(cap.parameterDefinitions())
                .anyMatch(p -> "minimumScore".equals(p.name()) && "SCORE".equals(p.type()));
        assertThat(cap.productionSupported()).isTrue();
        assertThat(cap.studioSupported()).isTrue();
        assertThat(cap.implementationBindings().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void bankChequeBounce_exposesWindowAndCount() {
        BusinessCapability cap = service.findById("BANK.CHEQUE_BOUNCE_MAX").orElseThrow();
        assertThat(cap.parameterDefinitions().stream().map(ParameterDefinition::name).toList())
                .contains("windowMonths", "maximumCount");
    }

    @Test
    void finFoirMax_isAvailable() {
        BusinessCapability cap = service.findById("FIN.FOIR_MAX").orElseThrow();
        assertThat(cap.domain()).isEqualTo(CapabilityDomain.FINANCIAL);
        assertThat(cap.parameterDefinitions())
                .anyMatch(p -> "maximumPercentage".equals(p.name()));
        assertThat(cap.productionSupported()).isTrue();
    }

    @Test
    void productionBureauScoreRule_mapsToBureauMinScore() {
        Map<String, Object> mapped = service.mapProductionHardRule(Map.of(
                "id", "scf_hard_bureau",
                "parameter", "BUREAU_SCORE",
                "condition", "LT:650",
                "decision", "REJECT"));
        assertThat(mapped.get("matched")).isEqualTo(true);
        assertThat(mapped.get("businessCapabilityId")).isEqualTo("BUREAU.MIN_SCORE");
        assertThat(mapped.get("treatment")).isEqualTo("REJECT");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) mapped.get("parameters");
        assertThat(params.get("minimumScore")).isEqualTo(650L);
        assertThat(mapped.get("source")).isEqualTo("PRODUCTION_UNDERWRITING");
    }

    @Test
    void productionChequeBounceRule_mapsToBankChequeBounceMax() {
        Map<String, Object> mapped = service.mapProductionHardRule(Map.of(
                "id", "scf_hard_chq3",
                "parameter", "CHEQUE_BOUNCES_3M",
                "condition", "GT:0",
                "decision", "REJECT"));
        assertThat(mapped.get("matched")).isEqualTo(true);
        assertThat(mapped.get("businessCapabilityId")).isEqualTo("BANK.CHEQUE_BOUNCE_MAX");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) mapped.get("parameters");
        assertThat(params.get("windowMonths")).isEqualTo(3);
        assertThat(params.get("maximumCount")).isEqualTo(0L);
    }

    @Test
    void catalogueGroupsCorrectly_andOmitsEmptyGroups() {
        Map<String, Object> view = service.catalogueView(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> groups = (Map<String, Object>) view.get("groups");
        assertThat(groups).containsKeys("Bureau", "Banking", "Financial", "Eligibility", "KYC");
        assertThat(groups).doesNotContainKey("");
        assertThat(((List<?>) groups.get("Bureau"))).isNotEmpty();
        assertThat(view.get("capabilityCount")).isEqualTo(service.listCapabilities().size());
    }

    @Test
    void catalogueRetrieval_doesNotEnableProductionAuthority() {
        Map<String, Object> view = service.catalogueView(true);
        assertThat(view.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(view.get("productionAuthority")).isEqualTo("DISABLED");
    }

    @Test
    void allowCanonicalAuthority_remainsFalse() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        assertThat(props.getCutover().isAllowCanonicalAuthority()).isFalse();
        assertThat(service.catalogueView(false).get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void scfFixtureMappings_coverBureauAndBounce() {
        Map<String, Object> sample = service.mapProductionFixtureSample();
        assertThat((Number) sample.get("matchedCount")).isNotNull();
        assertThat(((Number) sample.get("matchedCount")).intValue()).isGreaterThanOrEqualTo(10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) sample.get("mappings");
        assertThat(mappings.stream().anyMatch(m -> "BUREAU.MIN_SCORE".equals(m.get("businessCapabilityId")))).isTrue();
        assertThat(mappings.stream().anyMatch(m -> "BANK.CHEQUE_BOUNCE_MAX".equals(m.get("businessCapabilityId")))).isTrue();
    }
}
