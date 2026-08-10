package com.los.core.creditintelligence.policy.fixture;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.ExecutablePackageStatus;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.support.ContentHasher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixture-only golden shadow packages for P1 tests. Never production-active.
 */
public final class GoldenShadowPackageFactory {

    private static final ContentHasher HASHER = new ContentHasher();

    private GoldenShadowPackageFactory() {}

    public static CiExecutablePolicyPackage bankingBreShadowV1(UUID tenantId) {
        List<Map<String, Object>> rules = new ArrayList<>();
        // Starter: ADB_3M >= PROPOSED_EDI
        rules.add(rule("BANK_STARTER_ADB_GTE_EDI", "BANKING", "HARD",
                PolicyDsl.gte(PolicyDsl.metric("banking.avg_daily_balance_3m"),
                        Map.of("policyParameter", "PROPOSED_EDI")),
                "PASS", "FAIL",
                List.of(Map.of("kind", "METRIC", "reference", "banking.avg_daily_balance_3m"),
                        Map.of("kind", "POLICY_PARAMETER", "reference", "PROPOSED_EDI"))));
        // DigiLeap: ADB/5 >= EDI AND txn_avg >= 20
        rules.add(rule("BANK_DIGILEAP_ADB_FIFTH_GTE_EDI", "BANKING", "HARD",
                PolicyDsl.and(
                        PolicyDsl.gte(
                                Map.of("op", "DIVIDE",
                                        "left", PolicyDsl.metric("banking.avg_daily_balance_3m"),
                                        "right", Map.of("const", 5)),
                                Map.of("policyParameter", "PROPOSED_EDI")),
                        PolicyDsl.gte(PolicyDsl.metric("banking.avg_monthly_txn_count_3m"), Map.of("const", 20))),
                "PASS", "FAIL",
                List.of(Map.of("kind", "METRIC", "reference", "banking.avg_daily_balance_3m"),
                        Map.of("kind", "POLICY_PARAMETER", "reference", "PROPOSED_EDI"))));
        // SmartSwitch: ADB_settlements/10 >= EDI AND settlement_count >= 20
        rules.add(rule("BANK_SMARTSWITCH_SETTLEMENT_TENTH", "BANKING", "HARD",
                PolicyDsl.and(
                        PolicyDsl.gte(
                                Map.of("op", "DIVIDE",
                                        "left", PolicyDsl.metric("banking.avg_daily_settlement_3m"),
                                        "right", Map.of("const", 10)),
                                Map.of("policyParameter", "PROPOSED_EDI")),
                        PolicyDsl.gte(PolicyDsl.metric("banking.avg_monthly_settlement_count_3m"), Map.of("const", 20))),
                "PASS", "FAIL", List.of()));
        // Reboost (>60k): ADB/5 >= EDI AND txn >= 30
        rules.add(rule("BANK_REBOOST_ADB_FIFTH_GTE_EDI", "BANKING", "HARD",
                PolicyDsl.and(
                        PolicyDsl.gte(
                                Map.of("op", "DIVIDE",
                                        "left", PolicyDsl.metric("banking.avg_daily_balance_3m"),
                                        "right", Map.of("const", 5)),
                                Map.of("policyParameter", "PROPOSED_EDI")),
                        PolicyDsl.gte(PolicyDsl.metric("banking.avg_monthly_txn_count_3m"), Map.of("const", 30))),
                "PASS", "FAIL", List.of()));

        Map<String, Object> content = baseContent("BANKING_BRE_SHADOW_V1", rules);
        content.put("stageDefinitions", List.of(stage("BANKING", 1, List.of(
                "BANK_STARTER_ADB_GTE_EDI",
                "BANK_DIGILEAP_ADB_FIFTH_GTE_EDI",
                "BANK_SMARTSWITCH_SETTLEMENT_TENTH",
                "BANK_REBOOST_ADB_FIFTH_GTE_EDI"))));
        content.put("metadata", Map.of(
                "fixtureOnly", true,
                "exactly100Resolution", true,
                "note", "PROPOSED_EDI fixture-only exactly-100 resolution marked in metadata"));
        return pkg(tenantId, "BANKING_BRE_SHADOW_V1", "1", content);
    }

    public static CiExecutablePolicyPackage bureauBreShadowV1(UUID tenantId) {
        List<Map<String, Object>> rules = new ArrayList<>();
        // Score: -1, NTC, or >= 650
        rules.add(rule("BUREAU_SCORE_ALLOWED", "BUREAU", "KNOCKOUT",
                PolicyDsl.or(
                        PolicyDsl.eq(PolicyDsl.metric("bureau.score"), Map.of("const", -1)),
                        PolicyDsl.eq(PolicyDsl.metric("bureau.ntc"), Map.of("const", true)),
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))),
                "PASS", "FAIL", List.of()));
        rules.add(rule("BUREAU_NO_WRITEOFF_EXCEPT_CC", "BUREAU", "HARD",
                PolicyDsl.eq(PolicyDsl.metric("bureau.writeoff.non_cc_count"), Map.of("const", 0)),
                "PASS", "FAIL", List.of()));
        rules.add(rule("BUREAU_OVERDUE_EXCEPTION_GATE", "BUREAU", "HARD",
                PolicyDsl.or(
                        PolicyDsl.eq(PolicyDsl.metric("bureau.overdue.amount"), Map.of("const", 0)),
                        PolicyDsl.and(
                                PolicyDsl.gt(PolicyDsl.metric("bureau.overdue.age_days"), Map.of("const", 365)),
                                PolicyDsl.eq(PolicyDsl.metric("bureau.overdue.has_new_loans_after"), Map.of("const", true)),
                                PolicyDsl.gte(PolicyDsl.metric("bureau.overdue.clean_months"), Map.of("const", 6)),
                                PolicyDsl.lt(PolicyDsl.metric("bureau.overdue.amount"), Map.of("const", 1500)))),
                "PASS", "FAIL", List.of()));
        rules.add(rule("BUREAU_CC_OVERDUE_MAX", "BUREAU", "HARD",
                PolicyDsl.lte(PolicyDsl.metric("bureau.credit_card.overdue_amount"), Map.of("const", 5000)),
                "PASS", "FAIL", List.of()));
        rules.add(rule("BUREAU_MAX_DPD_6M", "BUREAU", "HARD",
                PolicyDsl.lte(PolicyDsl.metric("bureau.max_dpd_6m"), Map.of("const", 30)),
                "PASS", "FAIL", List.of()));
        rules.add(rule("BUREAU_STATUS_REJECT", "BUREAU", "HARD",
                PolicyDsl.not(Map.of("op", "IN",
                        "left", PolicyDsl.metric("bureau.worst_status"),
                        "set", List.of("SETTLED", "RESTRUCTURED", "ACCOUNT_SOLD", "DBT", "PWOS", "LSS", "SUIT_FILED"))),
                "PASS", "FAIL", List.of()));
        rules.add(rule("BUREAU_INQUIRIES_CURRENT_MONTH", "BUREAU", "HARD",
                PolicyDsl.lte(PolicyDsl.metric("bureau.inquiries.current_month_count"), Map.of("const", 3)),
                "PASS", "FAIL", List.of()));

        Map<String, Object> content = baseContent("BUREAU_BRE_SHADOW_V1", rules);
        content.put("stageDefinitions", List.of(stage("BUREAU", 1,
                rules.stream().map(r -> String.valueOf(r.get("ruleId"))).toList())));
        return pkg(tenantId, "BUREAU_BRE_SHADOW_V1", "1", content);
    }

    public static CiExecutablePolicyPackage legacyEquivalentPolicyV1(UUID tenantId) {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(rule("LEGACY_LIVE_UNSECURED_DEFAULT", "ELIGIBILITY", "HARD",
                PolicyDsl.lte(PolicyDsl.metric("bureau.live_unsecured_loan_count"), Map.of("const", 6)),
                "PASS", "FAIL",
                List.of(Map.of("kind", "METRIC", "reference", "bureau.live_unsecured_loan_count"))));
        Map<String, Object> content = baseContent("LEGACY_EQUIVALENT_POLICY_V1", rules);
        content.put("eligibility", Map.of(
                "flag", "NOT_ELIGIBLE_FOR_NEW_LENDER_USE",
                "documentedDefaults", List.of(
                        "live_unsecured_loan_count defaulted to 0 in legacy gap path",
                        "SCF_GAP_ITR_INCOME silent default retained only in legacy CreditControl")));
        content.put("stageDefinitions", List.of(stage("ELIGIBILITY", 1, List.of("LEGACY_LIVE_UNSECURED_DEFAULT"))));
        content.put("metadata", Map.of(
                "NOT_ELIGIBLE_FOR_NEW_LENDER_USE", true,
                "legacyDefaultsDocumented", true));
        return pkg(tenantId, "LEGACY_EQUIVALENT_POLICY_V1", "1", content);
    }

    public static CiExecutablePolicyPackage canonicalShadowPolicyV1(UUID tenantId) {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(rule("CANONICAL_LIVE_UNSECURED_NO_DEFAULT", "ELIGIBILITY", "HARD",
                PolicyDsl.lte(PolicyDsl.metric("bureau.live_unsecured_loan_count"), Map.of("const", 6)),
                "PASS", "FAIL",
                List.of(Map.of("kind", "METRIC", "reference", "bureau.live_unsecured_loan_count"))));
        Map<String, Object> content = baseContent("CANONICAL_SHADOW_POLICY_V1", rules);
        content.put("stageDefinitions", List.of(stage("ELIGIBILITY", 1, List.of("CANONICAL_LIVE_UNSECURED_NO_DEFAULT"))));
        content.put("metadata", Map.of(
                "noSilentDefaults", true,
                "missingYieldsDataInsufficient", true));
        return pkg(tenantId, "CANONICAL_SHADOW_POLICY_V1", "1", content);
    }

    private static Map<String, Object> baseContent(String code, List<Map<String, Object>> rules) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("policyCode", code);
        content.put("rules", rules);
        content.put("shadowOnly", true);
        content.put("activationForbidden", true);
        return content;
    }

    private static Map<String, Object> stage(String code, int seq, List<String> ruleIds) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("stageCode", code);
        s.put("sequence", seq);
        s.put("rules", ruleIds);
        s.put("continueOnFail", true);
        s.put("continueOnRefer", true);
        s.put("required", true);
        return s;
    }

    private static Map<String, Object> rule(
            String id, String stage, String type, Map<String, Object> expression,
            String onTrue, String onFalse, List<Map<String, Object>> inputRefs) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ruleId", id);
        r.put("stageCode", stage);
        r.put("ruleType", type);
        r.put("expression", expression);
        r.put("onTrue", onTrue);
        r.put("onFalse", onFalse);
        r.put("onMissing", PolicyDslInterpreterV1.DATA_INSUFFICIENT);
        r.put("allowsDefaulted", false);
        r.put("inputRefs", inputRefs);
        return r;
    }

    private static CiExecutablePolicyPackage pkg(UUID tenantId, String code, String version,
                                                 Map<String, Object> content) {
        String hash = HASHER.hashMap(content);
        return CiExecutablePolicyPackage.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .policyCode(code)
                .version(version)
                .status(ExecutablePackageStatus.SHADOW.name())
                .dslVersion(PolicyDslInterpreterV1.DSL_VERSION)
                .evaluationSemanticsVersion(PolicyDslInterpreterV1.EVALUATION_SEMANTICS)
                .content(content)
                .contentHash(hash)
                .createdAt(Instant.now())
                .createdBy("GoldenShadowPackageFactory")
                .publishedAt(Instant.now())
                .publishedBy("fixture")
                .approvalMetadata(Map.of("shadowOnly", true, "neverActive", true))
                .build();
    }
}
