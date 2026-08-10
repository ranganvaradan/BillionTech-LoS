package com.los.core.creditintelligence.decisionpolicy.sim;

import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyStages;
import com.los.core.creditintelligence.decisionpolicy.KycRequirementType;
import com.los.core.creditintelligence.decisionpolicy.PolicyGuardrailClass;
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
 * Explicit VALIDATION FIXTURE Decision Policy packages for KYC-7 E2E simulation.
 * Contains KYC + Credit + scorecard + embedded decision strategy. Never production-active.
 */
public final class GoldenDecisionPolicyE2EPackageFactory {

    public static final String POLICY_CODE_V1 = "DECISION_POLICY_E2E_V1";
    public static final String POLICY_CODE_V2 = "DECISION_POLICY_E2E_V2";
    public static final String DEMO_LABEL = "VALIDATION FIXTURE — NOT REAL BORROWER DATA";

    private static final ContentHasher HASHER = new ContentHasher();

    private GoldenDecisionPolicyE2EPackageFactory() {}

    public static CiExecutablePolicyPackage decisionPolicyE2eV1(UUID tenantId) {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.addAll(kycRules());
        rules.addAll(creditRules());

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("policyCode", POLICY_CODE_V1);
        content.put("rules", rules);
        content.put("shadowOnly", true);
        content.put("activationForbidden", true);
        content.put("allowCanonicalAuthority", false);
        content.put("stageDefinitions", List.of(
                stage(DecisionPolicyStages.KYC_ELIGIBILITY, 1,
                        kycRules().stream().map(r -> String.valueOf(r.get("ruleId"))).toList()),
                stage(DecisionPolicyStages.CREDIT_UNDERWRITING, 2,
                        creditRules().stream().map(r -> String.valueOf(r.get("ruleId"))).toList())));
        content.put("scorecard", scorecard());
        content.put("decisionStrategy", DecisionStrategyFactory.p2ValidationStrategyV1(tenantId).getContent());
        content.put("policyParameters", Map.of(
                "PROPOSED_EDI", 15_000,
                "MAX_FOIR", 0.50,
                "POLICY_CAP", 900_000));
        content.put("metadata", Map.of(
                "fixtureOnly", true,
                "demoLabel", DEMO_LABEL,
                "policyType", "DECISION_POLICY",
                "e2eSimulation", true));
        return pkg(tenantId, POLICY_CODE_V1, "1", content);
    }

    /** v2 adds mandatory CKYC — historical v1 replay must stay pinned. */
    public static CiExecutablePolicyPackage decisionPolicyE2eV2(UUID tenantId) {
        CiExecutablePolicyPackage v1 = decisionPolicyE2eV1(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> content = new LinkedHashMap<>(v1.getContent());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = new ArrayList<>(
                (List<Map<String, Object>>) content.getOrDefault("rules", List.of()));
        rules.add(kycRule(
                "KYC_CKYC_REQUIRED",
                "CKYC download/verification required",
                DecisionPolicyDomain.KYC,
                KycRequirementType.VERIFICATION,
                PolicyGuardrailClass.NBFC_CONFIGURABLE,
                "HARD",
                PolicyDsl.eq(PolicyDsl.fact("kyc.ckyc.verified"), Map.of("const", true)),
                "PASS", "FAIL",
                List.of("kyc.ckyc.verified")));
        content.put("rules", rules);
        content.put("policyCode", POLICY_CODE_V2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = new ArrayList<>(
                (List<Map<String, Object>>) content.getOrDefault("stageDefinitions", List.of()));
        if (!stages.isEmpty()) {
            Map<String, Object> kycStage = new LinkedHashMap<>(stages.get(0));
            kycStage.put("rules", rules.stream()
                    .filter(ShadowKycPolicyEvaluationServiceAdapter::isKyc)
                    .map(r -> String.valueOf(r.get("ruleId")))
                    .toList());
            stages.set(0, kycStage);
            content.put("stageDefinitions", stages);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        if (content.get("metadata") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> meta.put(String.valueOf(k), v));
        }
        meta.put("supersedes", POLICY_CODE_V1);
        content.put("metadata", meta);
        return pkg(tenantId, POLICY_CODE_V2, "2", content);
    }

    /** Credit-only package — incomplete for Decision Policy simulation (no KYC). */
    public static CiExecutablePolicyPackage incompleteCreditOnlyPackage(UUID tenantId) {
        List<Map<String, Object>> rules = creditRules();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("policyCode", "CATALOGUE_CREDIT_ONLY_INCOMPLETE");
        content.put("rules", rules);
        content.put("shadowOnly", true);
        content.put("stageDefinitions", List.of(
                stage(DecisionPolicyStages.CREDIT_UNDERWRITING, 1,
                        rules.stream().map(r -> String.valueOf(r.get("ruleId"))).toList())));
        content.put("metadata", Map.of("fixtureOnly", false, "catalogueLinked", true));
        return pkg(tenantId, "CATALOGUE_CREDIT_ONLY_INCOMPLETE", "1", content);
    }

    private static List<Map<String, Object>> kycRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(kycRule(
                "KYC_PAN_VERIFIED",
                "PAN must be verified",
                DecisionPolicyDomain.KYC,
                KycRequirementType.VERIFICATION,
                PolicyGuardrailClass.NBFC_CONFIGURABLE,
                "HARD",
                PolicyDsl.eq(PolicyDsl.fact("kyc.pan.verified"), Map.of("const", true)),
                "PASS", "FAIL",
                List.of("kyc.pan.verified")));
        rules.add(kycRule(
                "KYC_PAN_NAME_MATCH_REFER",
                "PAN name mismatch requires manual verification",
                DecisionPolicyDomain.KYC,
                KycRequirementType.MANUAL_VERIFICATION,
                PolicyGuardrailClass.NBFC_CONFIGURABLE,
                "SOFT",
                PolicyDsl.iff(
                        PolicyDsl.eq(PolicyDsl.fact("kyc.pan.name_match"), Map.of("const", false)),
                        Map.of("const", "REFER"),
                        Map.of("const", "PASS")),
                "PASS", "PASS",
                List.of("kyc.pan.name_match")));
        rules.add(kycRule(
                "KYC_VKYC_ABOVE_5L",
                "VKYC required when requested amount above 5 lakh",
                DecisionPolicyDomain.ELIGIBILITY,
                KycRequirementType.COMPLETION_REQUIREMENT,
                PolicyGuardrailClass.NBFC_CONFIGURABLE,
                "HARD",
                PolicyDsl.or(
                        PolicyDsl.lte(PolicyDsl.appField("requested_amount"), Map.of("const", 500_000)),
                        PolicyDsl.eq(PolicyDsl.fact("kyc.vkyc.completed"), Map.of("const", true))),
                "PASS", "FAIL",
                List.of("kyc.vkyc.completed", "application.requested_amount")));
        rules.add(kycRule(
                "KYC_COMPANY_CIN_GST",
                "Company borrowers require CIN and GSTIN verification",
                DecisionPolicyDomain.ELIGIBILITY,
                KycRequirementType.VERIFICATION,
                PolicyGuardrailClass.NBFC_CONFIGURABLE,
                "HARD",
                PolicyDsl.or(
                        PolicyDsl.ne(PolicyDsl.appField("borrower_type"), Map.of("const", "COMPANY")),
                        PolicyDsl.and(
                                PolicyDsl.eq(PolicyDsl.fact("kyc.cin.verified"), Map.of("const", true)),
                                PolicyDsl.eq(PolicyDsl.fact("kyc.gstin.verified"), Map.of("const", true)))),
                "PASS", "FAIL",
                List.of("kyc.cin.verified", "kyc.gstin.verified")));
        return rules;
    }

    private static List<Map<String, Object>> creditRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(creditRule(
                "CREDIT_BUREAU_SCORE_MIN",
                "Bureau score must be >= 650 or NTC",
                "KNOCKOUT",
                PolicyDsl.or(
                        PolicyDsl.eq(PolicyDsl.metric("bureau.ntc"), Map.of("const", true)),
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650))),
                "PASS", "FAIL"));
        rules.add(creditRule(
                "CREDIT_BUREAU_NO_WRITEOFF",
                "No non-CC write-offs",
                "HARD",
                PolicyDsl.eq(PolicyDsl.metric("bureau.writeoff.non_cc_count"), Map.of("const", 0)),
                "PASS", "FAIL"));
        rules.add(creditRule(
                "CREDIT_BANK_ADB_GTE_EDI",
                "Average daily balance supports proposed EDI",
                "HARD",
                PolicyDsl.gte(PolicyDsl.metric("banking.avg_daily_balance_3m"),
                        Map.of("policyParameter", "PROPOSED_EDI")),
                "PASS", "FAIL"));
        rules.add(creditRule(
                "CREDIT_GST_BANK_VARIANCE_REFER",
                "Material GST vs bank turnover variance requires referral",
                "SOFT",
                PolicyDsl.or(
                        PolicyDsl.lte(PolicyDsl.metric("recon.gst_bank_turnover_variance_pct"), Map.of("const", 25)),
                        PolicyDsl.eq(PolicyDsl.metric("recon.gst_bank_turnover_variance_pct"), Map.of("const", 0))),
                "PASS", "REFER"));
        return rules;
    }

    private static Map<String, Object> scorecard() {
        Map<String, Object> sc = new LinkedHashMap<>();
        sc.put("scorecardCode", "E2E_VALIDATION_SCORECARD_V1");
        sc.put("label", "VALIDATION FIXTURE SCORECARD");
        sc.put("ownership", "POLICY_PACKAGE");

        Map<String, Object> bureauComp = new LinkedHashMap<>();
        bureauComp.put("metric", "bureau.score");
        bureauComp.put("weight", 1.0);
        bureauComp.put("critical", true);
        bureauComp.put("bands", List.of(
                bandPoints(750, 100),
                bandPoints(700, 80),
                bandPoints(650, 60),
                bandPoints(0, 20)));

        Map<String, Object> bankComp = new LinkedHashMap<>();
        bankComp.put("metric", "banking.avg_daily_balance_3m");
        bankComp.put("weight", 0.5);
        bankComp.put("critical", false);
        bankComp.put("bands", List.of(
                bandPoints(100000, 100),
                bandPoints(50000, 70),
                bandPoints(15000, 40),
                bandPoints(0, 10)));

        sc.put("components", List.of(bureauComp, bankComp));
        sc.put("bands", List.of(
                Map.of("min", 90, "grade", "A"),
                Map.of("min", 70, "grade", "B"),
                Map.of("min", 50, "grade", "C"),
                Map.of("min", 0, "grade", "D")));
        return sc;
    }

    private static Map<String, Object> bandPoints(int min, int points) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min", min);
        m.put("points", points);
        return m;
    }

    private static Map<String, Object> kycRule(
            String id, String title, DecisionPolicyDomain domain, KycRequirementType reqType,
            PolicyGuardrailClass guardrail, String ruleType, Map<String, Object> expression,
            String onTrue, String onFalse, List<String> facts) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ruleId", id);
        r.put("businessRuleName", title);
        r.put("stageCode", DecisionPolicyStages.KYC_ELIGIBILITY);
        r.put("ruleType", ruleType);
        r.put("decisionDomain", domain.name());
        r.put("kycRequirementType", reqType.name());
        r.put("guardrailClass", guardrail.name());
        r.put("expression", expression);
        r.put("onTrue", onTrue);
        r.put("onFalse", onFalse);
        r.put("onMissing", PolicyDslInterpreterV1.DATA_INSUFFICIENT);
        r.put("mandatory", !"SOFT".equalsIgnoreCase(ruleType));
        r.put("policySelectsProvider", false);
        List<Map<String, Object>> refs = new ArrayList<>();
        for (String f : facts) {
            if (f.startsWith("application.")) {
                refs.add(Map.of("kind", "APPLICATION_FIELD", "reference", f.substring("application.".length())));
            } else {
                refs.add(Map.of("kind", "FACT", "reference", f));
            }
        }
        r.put("inputRefs", refs);
        return r;
    }

    private static Map<String, Object> creditRule(
            String id, String title, String type, Map<String, Object> expression,
            String onTrue, String onFalse) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ruleId", id);
        r.put("businessRuleName", title);
        r.put("stageCode", DecisionPolicyStages.CREDIT_UNDERWRITING);
        r.put("ruleType", type);
        r.put("decisionDomain", DecisionPolicyDomain.CREDIT.name());
        r.put("expression", expression);
        r.put("onTrue", onTrue);
        r.put("onFalse", onFalse);
        r.put("onMissing", PolicyDslInterpreterV1.DATA_INSUFFICIENT);
        r.put("allowsDefaulted", false);
        return r;
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

    private static CiExecutablePolicyPackage pkg(UUID tenantId, String code, String version,
                                                 Map<String, Object> content) {
        String hash = HASHER.hashMap(content);
        return CiExecutablePolicyPackage.builder()
                .id(UUID.nameUUIDFromBytes((code + ":" + version).getBytes()))
                .tenantId(tenantId)
                .policyCode(code)
                .version(version)
                .status(ExecutablePackageStatus.SHADOW.name())
                .dslVersion(PolicyDslInterpreterV1.DSL_VERSION)
                .evaluationSemanticsVersion(PolicyDslInterpreterV1.EVALUATION_SEMANTICS)
                .content(content)
                .contentHash(hash)
                .createdAt(Instant.parse("2024-06-15T00:00:00Z"))
                .createdBy("GoldenDecisionPolicyE2EPackageFactory")
                .publishedAt(Instant.parse("2024-06-15T00:00:00Z"))
                .publishedBy("fixture")
                .approvalMetadata(Map.of(
                        "shadowOnly", true,
                        "neverActive", true,
                        "allowCanonicalAuthority", false,
                        "fixtureOnly", true,
                        "demoLabel", DEMO_LABEL))
                .build();
    }

    /** Tiny adapter to avoid circular import of ShadowKycPolicyEvaluationService in stage rebuild. */
    private static final class ShadowKycPolicyEvaluationServiceAdapter {
        static boolean isKyc(Map<String, Object> rule) {
            return com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService
                    .isKycEligibilityRule(rule);
        }
    }
}
