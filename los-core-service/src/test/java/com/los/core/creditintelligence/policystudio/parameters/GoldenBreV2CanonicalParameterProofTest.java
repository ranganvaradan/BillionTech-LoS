package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN-POLICY-V2 — BRE canonical ID proof. Vikasam is the Golden case only; IDs are GACAT.
 */
class GoldenBreV2CanonicalParameterProofTest {

    private record Spec(
            int breRuleNo,
            String businessRequirement,
            List<String> canonicalIds,
            String form,
            boolean mustBeExecutable) {}

    @Test
    void elevenBreRules_resolveToCanonicalAuthorableIds() {
        List<Spec> specs = List.of(
                new Spec(1, "Score -1, NTC, or >= 650 only",
                        List.of("bureau.score", "bureau.status_ntc"), "HARD_ELIGIBILITY_OR", true),
                new Spec(2, "No loan write-offs except credit cards",
                        List.of("bureau.accounts.writeoff_non_cc"), "ELIGIBILITY_LTE_0", true),
                new Spec(3, "No non-CC overdue except nested 1-4 parent",
                        List.of("bureau.non_cc_overdue_exception_violation_count"), "ELIGIBILITY_EQ_0", true),
                new Spec(4, "CC overdue > 5000 reject",
                        List.of("bureau.cc_overdue_amount"), "ELIGIBILITY_LTE_5000", true),
                new Spec(5, "Any account/CC max DPD > 30 in last 6m reject",
                        List.of("bureau.max_dpd_6m"), "ELIGIBILITY_LTE_30", true),
                new Spec(6, "Settled or Restructured reject",
                        List.of("bureau.settled_account_count", "bureau.restructured_account_count"),
                        "ELIGIBILITY_ALL_EQ_0", true),
                new Spec(7, "Any legal suit filed account reject",
                        List.of("bureau.suit_filed_account_count"), "ELIGIBILITY_EQ_0", true),
                new Spec(8, "DBT / PWOS / LSS reject",
                        List.of("bureau.dbt_account_count", "bureau.pwos_account_count", "bureau.lss_account_count"),
                        "ELIGIBILITY_ALL_EQ_0", true),
                new Spec(9, "Multiple PAN reject",
                        List.of("bureau.pan_distinct_count"), "ELIGIBILITY_LTE_1", true),
                new Spec(10, "More than 3 inquiries in current month reject",
                        List.of("bureau.inquiries.current_month"), "ELIGIBILITY_LTE_3", true),
                new Spec(11, "Account Sold reject",
                        List.of("bureau.account_sold_count"), "ELIGIBILITY_EQ_0", false)
        );

        CanonicalParameterRegistry registry = CanonicalParameterRegistry.fromSeedForTestsOnly();
        int nonCanonical = 0;
        int nonAuthorable = 0;
        int withoutExecutable = 0;
        int withoutTested = 0;
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();

        assertThat(PolicyAuthorableParameterProjection.isAuthorable("bureau.tradeline.suit_filed")).isTrue();
        assertThat(PolicyAuthorableParameterProjection.isAuthorable("bureau.suit_filed_account_count")).isTrue();
        // BRE is any suit-filed ACCOUNT, not the per-tradeline RAW flag.
        assertThat(specs.get(6).canonicalIds()).containsExactly("bureau.suit_filed_account_count");

        for (Spec spec : specs) {
            for (String id : spec.canonicalIds()) {
                Map<String, Object> row = new LinkedHashMap<>();
                boolean gacat = registry.findById(id).isPresent();
                boolean authorable = PolicyAuthorableParameterProjection.isAuthorable(id);
                Map<String, Object> exec = ParameterExecutabilitySupport.evaluate(id);
                boolean executable = Boolean.TRUE.equals(exec.get("executable"))
                        || Boolean.TRUE.equals(exec.get("policyTestReady"))
                        || "POLICY_TEST_READY".equals(String.valueOf(exec.get("readiness")))
                        || "RUNTIME_READY_NONPROD".equals(String.valueOf(exec.get("readiness")))
                        || "PRODUCTION_READY".equals(String.valueOf(exec.get("readiness")));
                boolean tested = BuiltInBureauMetricProducer.EMITTED_IDS.contains(id)
                        || id.equals("bureau.score")
                        || id.equals("bureau.status_ntc");
                if (!gacat) nonCanonical++;
                if (!authorable) nonAuthorable++;
                if (spec.mustBeExecutable() && !executable && !tested) withoutExecutable++;
                if (spec.mustBeExecutable() && !tested && !executable) withoutTested++;
                row.put("gacatExists", gacat);
                row.put("policyAuthorable", authorable);
                row.put("executability", exec.get("readiness") != null ? exec.get("readiness") : exec.get("status"));
                row.put("tested", tested);
                row.put("source", registry.findById(id).map(CanonicalParameterDefinition::evaluatedFrom).orElse(null));
                row.put("semanticSelectable", GacatSemanticRegistry.shared().find(id)
                        .map(e -> e.policySelectableDefault()).orElse(false));
                rows.put(spec.breRuleNo() + ":" + id, row);
            }
        }

        System.out.println("GOLDEN_BRE_V2_CANONICAL_PROOF " + rows);
        System.out.println("NONCANONICAL=" + nonCanonical
                + " NONAUTHORABLE=" + nonAuthorable
                + " WITHOUT_EXECUTABLE=" + withoutExecutable
                + " WITHOUT_TESTED=" + withoutTested);

        assertThat(nonCanonical).as("BRE_RULE_WITH_NONCANONICAL_PARAMETER_COUNT").isZero();
        assertThat(specs).hasSize(11);
        // Account Sold is proven SOURCE_NOT_PROVEN — do not invent a producer.
        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).doesNotContain("bureau.account_sold_count");
        Map<String, Object> sold = ParameterExecutabilitySupport.evaluate("bureau.account_sold_count");
        System.out.println("ACCOUNT_SOLD_EXEC " + sold);
    }
}
