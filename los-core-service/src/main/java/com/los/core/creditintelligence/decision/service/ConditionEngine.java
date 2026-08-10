package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiConditionRecommendation;
import com.los.core.creditintelligence.decision.domain.ConditionType;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ConditionEngine {

    public record ConditionResult(
            List<CiConditionRecommendation> conditions,
            List<Object> conditionsPrecedent,
            List<Object> conditionsSubsequent,
            List<String> reasonCodes
    ) {}

    public ConditionResult compute(Map<String, Object> conditionStrategy,
                                   DecisionRuntimeInput input,
                                   CollateralEngine.CollateralResult collateral,
                                   boolean dataInsufficient) {
        Map<String, Object> s = conditionStrategy == null ? Map.of() : conditionStrategy;
        boolean autoDi = !Boolean.FALSE.equals(s.get("autoFromDi"));
        boolean autoRecon = !Boolean.FALSE.equals(s.get("autoFromReconConflict"));
        boolean autoColl = !Boolean.FALSE.equals(s.get("autoFromCollateralShortfall"));

        List<CiConditionRecommendation> conditions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        if (autoDi && (dataInsufficient || "DATA_INSUFFICIENT".equalsIgnoreCase(input.policyOverallOutcome()))) {
            conditions.add(cond(ConditionType.DOCUMENT_REQUIREMENT, "ADDITIONAL_DATA_REQUIRED",
                    "Provide missing critical underwriting data.",
                    "Policy or metric DATA_INSUFFICIENT",
                    "POLICY", "DATA_INSUFFICIENT", "DISBURSEMENT"));
            reasons.add("CONDITION_FROM_DI");
        }

        if (autoDi) {
            for (Map<String, Object> rule : input.policyRuleResults()) {
                String outcome = String.valueOf(rule.getOrDefault("outcome", ""));
                if ("DATA_INSUFFICIENT".equalsIgnoreCase(outcome) || "REFER".equalsIgnoreCase(outcome)) {
                    String ruleId = String.valueOf(rule.getOrDefault("ruleId", "RULE"));
                    conditions.add(cond(ConditionType.CONDITION_PRECEDENT, "RESOLVE_" + ruleId,
                            "Resolve policy item: " + ruleId,
                            "Policy rule outcome " + outcome,
                            "POLICY_RULE", ruleId, "SANCTION"));
                    reasons.add("CONDITION_FROM_RULE:" + ruleId);
                }
            }
        }

        if (autoRecon) {
            for (Map.Entry<String, Object> e : input.reconciliations().entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> rm)) continue;
                String outcome = String.valueOf(rm.get("outcome"));
                if ("CONFLICT".equalsIgnoreCase(outcome)
                        || "MATERIAL_VARIANCE".equalsIgnoreCase(outcome)
                        || "DATA_INSUFFICIENT".equalsIgnoreCase(outcome)) {
                    conditions.add(cond(ConditionType.CONDITION_PRECEDENT, "RESOLVE_RECON_" + e.getKey(),
                            "Resolve reconciliation conflict: " + e.getKey(),
                            "Reconciliation outcome " + outcome,
                            "RECONCILIATION", e.getKey(), "SANCTION"));
                    reasons.add("CONDITION_FROM_RECON:" + e.getKey());
                }
            }
        }

        if (autoColl && collateral != null && collateral.shortfall() != null
                && collateral.shortfall().signum() > 0) {
            conditions.add(cond(ConditionType.CONDITION_PRECEDENT, "COLLATERAL_SHORTFALL_COVER",
                    "Provide additional collateral or reduce facility to cover LTV shortfall.",
                    "Collateral shortfall " + collateral.shortfall(),
                    "COLLATERAL", "COLLATERAL_SHORTFALL", "SANCTION"));
            reasons.add("CONDITION_FROM_COLLATERAL");
        }

        List<Object> precedent = new ArrayList<>();
        List<Object> subsequent = new ArrayList<>();
        for (CiConditionRecommendation c : conditions) {
            Map<String, Object> row = Map.of(
                    "type", c.getConditionType(),
                    "code", c.getCode() == null ? "" : c.getCode(),
                    "description", c.getDescription() == null ? "" : c.getDescription(),
                    "reason", c.getReason() == null ? "" : c.getReason(),
                    "source", c.getSource() == null ? "" : c.getSource(),
                    "causingRuleOrRecon", c.getCausingRuleOrRecon() == null ? "" : c.getCausingRuleOrRecon(),
                    "mandatoryBefore", c.getMandatoryBefore() == null ? "" : c.getMandatoryBefore());
            if (ConditionType.CONDITION_SUBSEQUENT.name().equals(c.getConditionType())
                    || ConditionType.MONITORING_REQUIREMENT.name().equals(c.getConditionType())) {
                subsequent.add(row);
            } else {
                precedent.add(row);
            }
        }
        return new ConditionResult(conditions, precedent, subsequent, reasons);
    }

    private CiConditionRecommendation cond(ConditionType type, String code, String desc,
                                           String reason, String source, String causing, String before) {
        return CiConditionRecommendation.builder()
                .conditionType(type.name())
                .code(code)
                .description(desc)
                .reason(reason)
                .source(source)
                .causingRuleOrRecon(causing)
                .mandatoryBefore(before)
                .evidenceRefs(List.of(Map.of("kind", source, "reference", causing)))
                .build();
    }
}
