package com.los.core.creditintelligence.decisionpolicy.sim;

import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Frozen Decision Policy simulation case — VALIDATION FIXTURE only. */
public record FrozenDecisionSimulationCase(
        String caseCode,
        String displayName,
        String scenarioLabel,
        String productCode,
        UUID applicationId,
        UUID evaluationContextId,
        BigDecimal requestedAmount,
        Integer requestedTenureMonths,
        List<NormalizedKycFactBuilder.StepEvidence> kycSteps,
        Map<String, Object> applicationFields,
        Map<String, Object> applicationHints,
        Map<String, Object> facts,
        Map<String, Object> metrics,
        Map<String, Object> reconciliations,
        Map<String, Object> policyParameters,
        String legacyKycOutcome,
        String legacyCreditOutcome
) {
    public FrozenDecisionSimulationCase {
        kycSteps = kycSteps == null ? List.of() : List.copyOf(kycSteps);
        applicationFields = applicationFields == null ? Map.of() : Map.copyOf(applicationFields);
        applicationHints = applicationHints == null ? Map.of() : Map.copyOf(applicationHints);
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        reconciliations = reconciliations == null ? Map.of() : Map.copyOf(reconciliations);
        policyParameters = policyParameters == null ? Map.of() : Map.copyOf(policyParameters);
        applicationId = applicationId == null
                ? UUID.nameUUIDFromBytes(("app:" + caseCode).getBytes()) : applicationId;
        evaluationContextId = evaluationContextId == null
                ? UUID.nameUUIDFromBytes(("ctx:" + caseCode).getBytes()) : evaluationContextId;
        requestedTenureMonths = requestedTenureMonths == null ? 24 : requestedTenureMonths;
        productCode = productCode == null ? "TERM_LOAN" : productCode;
    }
}
