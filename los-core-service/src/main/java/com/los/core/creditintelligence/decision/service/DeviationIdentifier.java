package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiPolicyDeviation;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.domain.DeviationStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Identifies deviations — never auto-approves in P2.
 */
@Service
public class DeviationIdentifier {

    public record DeviationResult(
            List<CiPolicyDeviation> deviations,
            int materialCount,
            List<String> reasonCodes
    ) {}

    public DeviationResult identify(DecisionRuntimeInput input, UUID tenantId) {
        List<CiPolicyDeviation> deviations = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int material = 0;

        for (Map<String, Object> rule : input.policyRuleResults()) {
            String outcome = String.valueOf(rule.getOrDefault("outcome", ""));
            String type = String.valueOf(rule.getOrDefault("ruleType", ""));
            if ("FAIL".equalsIgnoreCase(outcome) || "REFER".equalsIgnoreCase(outcome)) {
                boolean hard = "HARD".equalsIgnoreCase(type) || "KNOCKOUT".equalsIgnoreCase(type);
                List<Object> compensating = compensatingFactors(input);
                CiPolicyDeviation d = CiPolicyDeviation.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .policyRuleId(String.valueOf(rule.getOrDefault("ruleId", "UNKNOWN")))
                        .originalOutcome(outcome)
                        .requestedOverride("CONTINUE_WITH_EXCEPTION")
                        .reason("Identified from policy rule outcome — human review required")
                        .severity(hard ? "MATERIAL" : "MODERATE")
                        .compensatingFactors(compensating)
                        .authorityRequired(hard ? "CREDIT_COMMITTEE" : "CREDIT_MANAGER_L2")
                        .status(DeviationStatus.REQUESTED.name())
                        .build();
                deviations.add(d);
                reasons.add("DEVIATION_IDENTIFIED:" + d.getPolicyRuleId());
                if (hard) {
                    material++;
                }
            }
        }
        return new DeviationResult(deviations, material, reasons);
    }

    private List<Object> compensatingFactors(DecisionRuntimeInput input) {
        List<Object> factors = new ArrayList<>();
        if (DecisionValueHelper.has(input.metrics(), "collateral.value")) {
            factors.add("strong_collateral");
        }
        if (DecisionValueHelper.has(input.metrics(), "banking.avg_daily_balance_3m")
                || DecisionValueHelper.has(input.metrics(), "bank.abb.average")) {
            factors.add("strong_banking");
        }
        Object grade = input.scoreResult().get("grade");
        if ("A".equals(grade) || "B".equals(grade)) {
            factors.add("high_evidence_strength");
        }
        return factors;
    }
}
