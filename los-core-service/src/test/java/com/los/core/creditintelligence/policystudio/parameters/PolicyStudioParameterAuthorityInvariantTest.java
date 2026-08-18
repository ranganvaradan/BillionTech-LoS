package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.catalogue.CatalogueCapabilityExpressionBuilder;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.catalogue.GacatPolicyAuthorableCapabilityFactory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN-POLICY-V2-AUTHORING-AND-POLICY-STUDIO-CLOSURE-1 — Policy Studio authorable universe.
 * No Vikasam UUID / policy-name branching. Add Rule and Change Parameter share GACAT.
 */
class PolicyStudioParameterAuthorityInvariantTest {

    private final CanonicalParameterRegistry registry = CanonicalParameterRegistry.fromSeedForTestsOnly();
    private final CreditCapabilityCatalogueService catalogue = new CreditCapabilityCatalogueService();

    @Test
    void policyStudioParameterAuthorityInvariants_areZero() {
        int legacyConsumers = legacyCatalogueConsumerCount();
        int mismatch = addRuleChangeParameterMismatchCount();
        int technical = PolicyAuthorableParameterProjection.technicalMetadataAuthorableCount(registry);
        int mappedShownNotMapped = mappedRuleShownNotMappedCount();

        assertThat(legacyConsumers)
                .as("POLICY_STUDIO_LEGACY_PARAMETER_CATALOGUE_CONSUMER_COUNT")
                .isZero();
        assertThat(mismatch)
                .as("ADD_RULE_CHANGE_PARAMETER_AUTHORITY_MISMATCH_COUNT")
                .isZero();
        assertThat(technical)
                .as("TECHNICAL_METADATA_AUTHORABLE_COUNT")
                .isZero();
        assertThat(mappedShownNotMapped)
                .as("MAPPED_RULE_SHOWN_NOT_MAPPED_COUNT")
                .isZero();
    }

    @Test
    void independentBankingPolicy_canBeAuthoredFromSameUniverse() {
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("obligation.ratio")).isTrue();
        var cap = GacatPolicyAuthorableCapabilityFactory
                .findAuthorable(registry, "obligation.ratio")
                .orElseThrow();
        var built = CatalogueCapabilityExpressionBuilder.build(cap, Map.of(
                "operator", "LTE",
                "threshold", 50,
                "whenMatched", "PASS"
        ), "REJECT");
        assertThat(built.metricPath()).isEqualTo("obligation.ratio");
        assertThat(built.onTrue()).isEqualTo("PASS");
        assertThat(built.onFalse()).isEqualTo("FAIL");
    }

    @Test
    void productionProjection_hasNoVikasamOrPolicyNameBranch() {
        assertThat(PolicyAuthorableParameterProjection.AUTHORITY)
                .doesNotContain("Vikasam")
                .doesNotContain("bf1f60a9");
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("bureau.score")).isTrue();
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("obligation.ratio")).isTrue();
    }

    @SuppressWarnings("unchecked")
    private int legacyCatalogueConsumerCount() {
        int n = 0;
        for (boolean advanced : new boolean[]{false, true}) {
            Map<String, Object> view = catalogue.catalogueView(advanced);
            List<Map<String, Object>> caps = (List<Map<String, Object>>) view.get("capabilities");
            for (Map<String, Object> cap : caps) {
                Object id = cap.get("businessCapabilityId");
                if (Boolean.TRUE.equals(cap.get("legacyCapabilityTemplate"))
                        || (id != null && String.valueOf(id).startsWith("BUREAU."))
                        || (id != null && String.valueOf(id).startsWith("ELIG."))
                        || (id != null && String.valueOf(id).startsWith("FIN."))
                        || (id != null && String.valueOf(id).startsWith("BANK."))
                        || (id != null && !String.valueOf(id).contains("."))) {
                    n++;
                }
            }
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    private int addRuleChangeParameterMismatchCount() {
        Map<String, Object> change = PolicyAuthorableParameterProjection.catalogueView(registry);
        Map<String, Object> addFalse = catalogue.catalogueView(false);
        Map<String, Object> addTrue = catalogue.catalogueView(true);
        Set<String> changeIds = ids((List<Map<String, Object>>) change.get("parameters"), "id");
        Set<String> addIds = ids((List<Map<String, Object>>) addFalse.get("capabilities"), "businessCapabilityId");
        Set<String> addAdv = ids((List<Map<String, Object>>) addTrue.get("capabilities"), "businessCapabilityId");
        int n = 0;
        if (!changeIds.equals(addIds)) n++;
        if (!changeIds.equals(addAdv)) n++;
        return n;
    }

    private static Set<String> ids(List<Map<String, Object>> rows, String key) {
        Set<String> out = new HashSet<>();
        for (Map<String, Object> row : rows) {
            if (row.get(key) != null) {
                out.add(String.valueOf(row.get(key)));
            }
        }
        return out;
    }

    private int mappedRuleShownNotMappedCount() {
        int n = 0;
        List<Map<String, Object>> mapped = RuleOperandPresenter.buildOperands(
                "GACAT_BUREAU_SCORE",
                List.of("bureau.score"),
                Map.of("parameterId", "bureau.score"),
                Map.of());
        for (Map<String, Object> face : mapped) {
            if ("Not yet mapped".equals(face.get("availabilityLabel"))
                    || "Not yet mapped".equals(face.get("message"))) {
                n++;
            }
        }
        List<Map<String, Object>> unresolvedExisting = RuleOperandPresenter.buildOperands(
                "CM_UNKNOWN_METRIC_GTE",
                List.of("bureau.not_a_real_canonical_id"),
                Map.of("parameterId", "bureau.not_a_real_canonical_id"),
                Map.of());
        for (Map<String, Object> face : unresolvedExisting) {
            if ("Not yet mapped".equals(face.get("availabilityLabel"))
                    || "Not yet mapped".equals(face.get("message"))
                    || !Boolean.TRUE.equals(face.get("existingMappingUnresolved"))) {
                n++;
            }
        }
        return n;
    }
}
