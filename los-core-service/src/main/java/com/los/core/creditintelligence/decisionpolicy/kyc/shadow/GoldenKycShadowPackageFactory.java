package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

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
 * Fixture Decision Policy packages for KYC-5 shadow evaluation.
 * Never production-active. Labeled VALIDATION FIXTURE.
 */
public final class GoldenKycShadowPackageFactory {

    private static final ContentHasher HASHER = new ContentHasher();

    private GoldenKycShadowPackageFactory() {}

    /** v1 — PAN verify, name-match REFER, VKYC above threshold, company CIN+GST, platform guardrail. */
    public static CiExecutablePolicyPackage decisionPolicyKycV1(UUID tenantId) {
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

        rules.add(kycRule(
                "KYC_PLATFORM_GUARDRAIL_IDENTITY",
                "Platform guardrail — identity verification required",
                DecisionPolicyDomain.KYC,
                KycRequirementType.REGULATORY_GUARDRAIL,
                PolicyGuardrailClass.PLATFORM_GUARDRAIL,
                "KNOCKOUT",
                PolicyDsl.eq(PolicyDsl.fact("kyc.pan.verified"), Map.of("const", true)),
                "PASS", "FAIL",
                List.of("kyc.pan.verified")));

        Map<String, Object> content = base("DECISION_POLICY_KYC_V1", rules);
        content.put("stageDefinitions", List.of(stage(
                DecisionPolicyStages.KYC_ELIGIBILITY, 1,
                rules.stream().map(r -> String.valueOf(r.get("ruleId"))).toList())));
        content.put("metadata", Map.of(
                "fixtureOnly", true,
                "demoLabel", "VALIDATION FIXTURE — NOT REAL BORROWER DATA",
                "policyType", "DECISION_POLICY",
                "kycShadow", true));
        return pkg(tenantId, "DECISION_POLICY_KYC_V1", "1", content);
    }

    /** v2 — adds mandatory CKYC (stricter). Historical v1 replay must stay pinned. */
    public static CiExecutablePolicyPackage decisionPolicyKycV2(UUID tenantId) {
        CiExecutablePolicyPackage v1 = decisionPolicyKycV1(tenantId);
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
        content.put("policyCode", "DECISION_POLICY_KYC_V2");
        content.put("stageDefinitions", List.of(stage(
                DecisionPolicyStages.KYC_ELIGIBILITY, 1,
                rules.stream().map(r -> String.valueOf(r.get("ruleId"))).toList())));
        Map<String, Object> meta = new LinkedHashMap<>();
        if (content.get("metadata") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> meta.put(String.valueOf(k), v));
        }
        meta.put("supersedes", "DECISION_POLICY_KYC_V1");
        content.put("metadata", meta);
        return pkg(tenantId, "DECISION_POLICY_KYC_V2", "2", content);
    }

    private static Map<String, Object> kycRule(
            String id,
            String title,
            DecisionPolicyDomain domain,
            KycRequirementType reqType,
            PolicyGuardrailClass guardrail,
            String ruleType,
            Map<String, Object> expression,
            String onTrue,
            String onFalse,
            List<String> facts
    ) {
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

    private static Map<String, Object> base(String code, List<Map<String, Object>> rules) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("policyCode", code);
        content.put("rules", rules);
        content.put("shadowOnly", true);
        content.put("activationForbidden", true);
        content.put("allowCanonicalAuthority", false);
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
                .createdAt(Instant.now())
                .createdBy("GoldenKycShadowPackageFactory")
                .publishedAt(Instant.now())
                .publishedBy("fixture")
                .approvalMetadata(Map.of(
                        "shadowOnly", true,
                        "neverActive", true,
                        "allowCanonicalAuthority", false,
                        "demoLabel", "VALIDATION FIXTURE — NOT REAL BORROWER DATA"))
                .build();
    }
}
