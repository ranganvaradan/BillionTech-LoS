package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.catalogue.CatalogueCapabilityExpressionBuilder;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.catalogue.GacatPolicyAuthorableCapabilityFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN-POLICY-CONFIGURATION-TRUTH-AUDIT-1 — Add Rule and Change Parameter share GACAT authorable projection.
 */
class PolicyAuthorableParameterProjectionTest {

    private final CanonicalParameterRegistry registry = CanonicalParameterRegistry.fromSeedForTestsOnly();

    @Test
    void selectableBusinessParameters_areAuthorable_ingredientsAreNot() {
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("bureau.score")).isTrue();
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("bureau.suit_filed_account_count")).isTrue();
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("bureau.tradeline.payment_history")).isFalse();
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("aa.transport.note")).isFalse();
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("unknown.not.in.gacat")).isFalse();
    }

    @Test
    void policyStudioCatalogue_excludesNonSelectable_doesNotHardCodeIds() {
        Map<String, Object> studio = PolicyAuthorableParameterProjection.catalogueView(registry);
        Map<String, Object> full = registry.catalogueView();
        int studioCount = ((Number) studio.get("count")).intValue();
        int fullCount = ((Number) full.get("count")).intValue();
        assertThat(studioCount).isLessThan(fullCount);
        assertThat(studio.get("policyAuthorableOnly")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) studio.get("parameters");
        assertThat(params).extracting(m -> m.get("id")).doesNotContain("aa.transport.note");
        assertThat(params).extracting(m -> m.get("id")).contains("bureau.score");
    }

    @Test
    void addRuleCatalogue_usesSameAuthorableIdsAsChangeParameter() {
        Map<String, Object> change = PolicyAuthorableParameterProjection.catalogueView(registry);
        Map<String, Object> add = new CreditCapabilityCatalogueService().catalogueView(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changeParams = (List<Map<String, Object>>) change.get("parameters");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> addCaps = (List<Map<String, Object>>) add.get("capabilities");
        List<String> changeIds = changeParams.stream().map(m -> String.valueOf(m.get("id"))).sorted().toList();
        List<String> addIds = addCaps.stream()
                .map(m -> String.valueOf(m.get("businessCapabilityId")))
                .sorted()
                .toList();
        assertThat(addIds).isEqualTo(changeIds);
        assertThat(add.get("authorableProjectionAuthority"))
                .isEqualTo(PolicyAuthorableParameterProjection.AUTHORITY);
        assertThat(addIds).doesNotContain("BUREAU.MIN_SCORE");
        assertThat(new CreditCapabilityCatalogueService().listCapabilities())
                .extracting(c -> c.businessCapabilityId())
                .contains("BUREAU.MIN_SCORE");
    }

    @Test
    void gacatAddRule_eligibilityForm_doesNotReverseAdverseBranches() {
        var cap = GacatPolicyAuthorableCapabilityFactory
                .findAuthorable(registry, "bureau.written_off_account_count")
                .orElseThrow();
        var built = CatalogueCapabilityExpressionBuilder.build(cap, Map.of(
                "operator", "LTE",
                "threshold", 0,
                "whenMatched", "PASS"
        ), "REJECT");
        assertThat(built.onTrue()).isEqualTo("PASS");
        assertThat(built.onFalse()).isEqualTo("FAIL");
        assertThat(built.metricPath()).isEqualTo("bureau.written_off_account_count");
        assertThat(built.expression().get("op")).isEqualTo("LTE");
    }

    @Test
    void gacatAddRule_adverseWhenMatched_setsFailOnTrue() {
        var cap = GacatPolicyAuthorableCapabilityFactory
                .findAuthorable(registry, "bureau.written_off_account_count")
                .orElseThrow();
        var built = CatalogueCapabilityExpressionBuilder.build(cap, Map.of(
                "operator", "GTE",
                "threshold", 1,
                "whenMatched", "FAIL"
        ), "REJECT");
        assertThat(built.onTrue()).isEqualTo("FAIL");
        assertThat(built.onFalse()).isEqualTo("PASS");
    }

    @Test
    void advancedAddRuleCatalogue_doesNotReintroduceLegacyTemplates() {
        Map<String, Object> primary = new CreditCapabilityCatalogueService().catalogueView(false);
        Map<String, Object> advanced = new CreditCapabilityCatalogueService().catalogueView(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> primaryCaps = (List<Map<String, Object>>) primary.get("capabilities");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> advancedCaps = (List<Map<String, Object>>) advanced.get("capabilities");
        List<String> primaryIds = primaryCaps.stream()
                .map(m -> String.valueOf(m.get("businessCapabilityId"))).sorted().toList();
        List<String> advancedIds = advancedCaps.stream()
                .map(m -> String.valueOf(m.get("businessCapabilityId"))).sorted().toList();
        assertThat(advancedIds).isEqualTo(primaryIds);
        assertThat(advanced.get("policyAuthorableOnly")).isEqualTo(true);
        assertThat(PolicyAuthorableParameterProjection.technicalMetadataAuthorableCount(registry)).isZero();
    }
}
