package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.policystudio.parameters.execution.BuiltInBureauMetricProducer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FINAL-EQUIFAX-END-TO-END-CLOSURE-1 — Bureau BRE mapped to the Equifax canonical universe.
 * Does not author a live policy.
 */
class EquifaxBreCoverageTest {

    private record BreRule(
            String name,
            String expression,
            List<String> parameterIds,
            List<String> sourceDependencies,
            boolean executable,
            String blocker) {}

    @Test
    void bureauBre_mapsToCanonicalEquifaxParameters() {
        List<BreRule> rules = List.of(
                new BreRule("SCORE_NTC_OR_650",
                        "OR(bureau.score = -1, bureau.status_ntc, bureau.score >= 650)",
                        List.of("bureau.score", "bureau.status_ntc"),
                        List.of("bureau.score"),
                        true, null),
                new BreRule("WRITEOFF_EXCEPT_CC",
                        "bureau.accounts.writeoff_non_cc == 0",
                        List.of("bureau.accounts.writeoff_non_cc"),
                        List.of("bureau.tradeline.write_off_amount", "bureau.tradeline.account_status"),
                        true, null),
                new BreRule("NON_CC_OVERDUE_EXCEPTION",
                        "bureau.non_cc_overdue_exception_violation_count == 0",
                        List.of("bureau.non_cc_overdue_exception_violation_count"),
                        List.of("bureau.tradeline.overdue_amount", "bureau.tradeline.dpd_month"),
                        true, null),
                new BreRule("CC_OVERDUE_GT_5000",
                        "bureau.cc_overdue_amount <= 5000",
                        List.of("bureau.cc_overdue_amount"),
                        List.of("bureau.tradeline.overdue_amount"),
                        true, null),
                new BreRule("DPD_GT_30_LAST_6M",
                        "bureau.max_dpd_6m <= 30",
                        List.of("bureau.max_dpd_6m"),
                        List.of("bureau.tradeline.dpd_month"),
                        true, null),
                new BreRule("SETTLED_OR_RESTRUCTURED",
                        "bureau.settled_account_count == 0 AND bureau.restructured_account_count == 0",
                        List.of("bureau.settled_account_count", "bureau.restructured_account_count"),
                        List.of("bureau.tradeline.account_status", "bureau.tradeline.payment_status_month"),
                        true, null),
                new BreRule("LEGAL_SUIT",
                        "bureau.suit_filed_account_count == 0",
                        List.of("bureau.suit_filed_account_count"),
                        List.of("bureau.tradeline.suit_filed"),
                        true, null),
                new BreRule("DBT_PWOS_LSS",
                        "bureau.dbt_account_count == 0 AND bureau.pwos_account_count == 0 AND bureau.lss_account_count == 0",
                        List.of("bureau.dbt_account_count", "bureau.pwos_account_count", "bureau.lss_account_count"),
                        List.of("bureau.tradeline.payment_status_month"),
                        true, null),
                new BreRule("MULTIPLE_PAN",
                        "bureau.pan_distinct_count <= 1",
                        List.of("bureau.pan_distinct_count"),
                        List.of(),
                        true, null),
                new BreRule("INQUIRIES_CURRENT_MONTH_GT_3",
                        "bureau.inquiries.current_month <= 3",
                        List.of("bureau.inquiries.current_month"),
                        List.of("bureau.inquiry.date"),
                        true, null),
                new BreRule("ACCOUNT_SOLD",
                        "bureau.account_sold_count == 0",
                        List.of("bureau.account_sold_count"),
                        List.of("bureau.tradeline.account_status"),
                        false, "SOURCE_NOT_PROVEN")
        );

        int fully = 0;
        int blockedSource = 0;
        int blockedBiz = 0;
        int dslGap = 0;
        for (BreRule r : rules) {
            if (r.executable()) {
                fully++;
                for (String id : r.parameterIds()) {
                    if (!id.equals("bureau.account_sold_count")) {
                        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS.contains(id)
                                || id.equals("bureau.score")
                                || id.equals("bureau.status_ntc")
                                || BuiltInBureauMetricProducer.EMITTED_IDS.contains(id)).isTrue();
                    }
                }
            } else if ("SOURCE_NOT_PROVEN".equals(r.blocker())) {
                blockedSource++;
            } else if ("BUSINESS_DEFINITION_REQUIRED".equals(r.blocker())) {
                blockedBiz++;
            } else {
                dslGap++;
            }
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("BRE_RULE_COUNT", rules.size());
        out.put("BRE_RULES_FULLY_SUPPORTED_COUNT", fully);
        out.put("BRE_RULES_BLOCKED_BY_SOURCE_COUNT", blockedSource);
        out.put("BRE_RULES_BLOCKED_BY_BUSINESS_DEFINITION_COUNT", blockedBiz);
        out.put("BRE_POLICY_DSL_GAP_COUNT", dslGap);
        System.out.println("EQUIFAX_BRE_COVERAGE " + out);
        assertThat(fully).isEqualTo(10);
        assertThat(blockedSource).isEqualTo(1);
        assertThat(blockedBiz).isZero();
        assertThat(dslGap).isZero();
        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).contains(
                "bureau.restructured_account_count",
                "bureau.dbt_account_count",
                "bureau.pwos_account_count",
                "bureau.lss_account_count");
        assertThat(BuiltInBureauMetricProducer.EMITTED_IDS).doesNotContain("bureau.account_sold_count");
    }
}
