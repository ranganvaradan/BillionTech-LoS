package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Curated KYC-5 shadow validation fixture matrix.
 * VALIDATION FIXTURE — NOT REAL BORROWER DATA / not production certification.
 */
public final class Kyc5FixtureMatrix {

    private Kyc5FixtureMatrix() {}

    public static List<Map<String, Object>> run(ShadowKycPolicyEvaluationService service) {
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CiExecutablePolicyPackage v1 = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
        List<Map<String, Object>> cases = new ArrayList<>();
        cases.add(caseRow(service, "CLEAN_PASS", v1, List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS"));
        cases.add(caseRow(service, "CONCLUSIVE_PAN_FAIL", v1, List.of(ShadowKycPolicyEvaluationService.panFail()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "FAIL"));
        cases.add(caseRow(service, "PROVIDER_OUTAGE", v1,
                List.of(ShadowKycPolicyEvaluationService.panProviderOutage()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "FAIL"));
        cases.add(caseRow(service, "NAME_MATCH_REFER", v1,
                List.of(new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.SUCCESS, null, Map.of("nameMatch", false), false, false)),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS"));
        cases.add(caseRow(service, "VKYC_REQUIRED_MISSING", v1, List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 600_000, "borrower_type", "INDIVIDUAL"), "PASS"));
        cases.add(caseRow(service, "VKYC_COMPLETED", v1,
                List.of(ShadowKycPolicyEvaluationService.panPass(),
                        new NormalizedKycFactBuilder.StepEvidence(
                                KycStepType.VIDEO_KYC, StepOutcome.SUCCESS, null, Map.of(), false, null)),
                Map.of("requested_amount", 600_000, "borrower_type", "INDIVIDUAL"), "PASS"));
        cases.add(caseRow(service, "COMPANY_CIN_GST", v1,
                List.of(ShadowKycPolicyEvaluationService.panPass(),
                        ShadowKycPolicyEvaluationService.step(KycStepType.CIN_MCA21, StepOutcome.SUCCESS, null, Map.of(), false),
                        ShadowKycPolicyEvaluationService.step(KycStepType.GSTIN_VERIFY, StepOutcome.SUCCESS, null, Map.of(), false)),
                Map.of("requested_amount", 200_000, "borrower_type", "COMPANY"), "PASS"));
        cases.add(caseRow(service, "MISSING_MANDATORY_PAN", v1, List.of(),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "INCOMPLETE"));
        return cases;
    }

    private static Map<String, Object> caseRow(
            ShadowKycPolicyEvaluationService service,
            String code,
            CiExecutablePolicyPackage pkg,
            List<NormalizedKycFactBuilder.StepEvidence> steps,
            Map<String, Object> appFields,
            String production
    ) {
        Map<String, Object> result = service.evaluate(ShadowKycEvaluationRequest.builder()
                .tenantId(pkg.getTenantId())
                .policyPackage(pkg)
                .routingOutcome("EXACTLY_ONE")
                .stepEvidence(steps)
                .applicationFields(appFields)
                .applicationHints(Map.of("panPresent", true))
                .productionKycOutcome(production)
                .persist(false)
                .build());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseCode", code);
        row.put("label", "VALIDATION FIXTURE");
        row.put("productionKyc", production);
        row.put("shadowKyc", result.get("overallOutcome"));
        row.put("comparisonClass", result.get("comparisonClass"));
        row.put("reviewRequired", result.get("reviewRequired"));
        row.put("deterministicHash", result.get("deterministicHash"));
        return row;
    }
}
