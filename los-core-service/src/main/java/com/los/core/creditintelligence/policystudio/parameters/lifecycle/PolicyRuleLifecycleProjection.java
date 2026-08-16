package com.los.core.creditintelligence.policystudio.parameters.lifecycle;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityProjection;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Authoritative per-rule projection. UI should render this — not re-derive badges.
 */
public final class PolicyRuleLifecycleProjection {

    private PolicyRuleLifecycleProjection() {}

    public record Facts(
            boolean ruleAccepted,
            boolean parameterResolved,
            boolean calculationRequired,
            boolean calculationDefined,
            boolean calculationValidated,
            boolean businessClarificationRequired,
            boolean dataAvailableForPolicyDesign,
            boolean policyTestReady,
            boolean runtimeReady,
            boolean productionReady,
            boolean includedExecutable,
            boolean authoringComplete,
            boolean knownExistingCalculationPending,
            boolean newCalculationProposalPending
    ) {}

    public static Map<String, Object> project(String ruleId, Facts f) {
        PolicyRuleLenderState state = deriveState(f);
        PolicyRuleOutstandingAction outstanding = deriveOutstanding(f, state);
        String primary = primaryActionLabel(outstanding, f);
        List<String> secondary = secondaryActions(state, f);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authority", "PolicyRuleLifecycleProjection");
        out.put("ruleId", ruleId);
        out.put("ruleAccepted", f.ruleAccepted());
        out.put("parameterId", null); // filled by enricher when known
        out.put("parameterResolved", f.parameterResolved());
        out.put("calculationRequired", f.calculationRequired());
        out.put("calculationDefined", f.calculationDefined());
        out.put("calculationValidated", f.calculationValidated());
        out.put("businessClarificationRequired", f.businessClarificationRequired());
        out.put("dataAvailableForPolicyDesign", f.dataAvailableForPolicyDesign());
        out.put("policyTestReady", f.policyTestReady());
        out.put("runtimeReady", f.runtimeReady());
        out.put("productionReady", f.productionReady());
        out.put("lenderState", state.name());
        out.put("lenderStateLabel", lenderLabel(state, f));
        out.put("ruleLifecycleLabel", ruleLifecycleLabel(state, f));
        out.put("outstandingAction", outstanding.name());
        out.put("lenderPrimaryAction", primary);
        out.put("lenderSecondaryActions", secondary);
        out.put("statusChip", statusChip(state, f));
        // Wave 10A — do not collapse parameter execution with rule review
        out.put("parameterExecutionAxisSeparate", true);
        out.put("showAcceptRule", outstanding == PolicyRuleOutstandingAction.ACCEPT_RULE);
        out.put("showAcceptCalculation",
                outstanding == PolicyRuleOutstandingAction.ACCEPT_EXISTING_CALCULATION
                        || outstanding == PolicyRuleOutstandingAction.APPROVE_NEW_CALCULATION);
        out.put("showCalculationSetup",
                outstanding == PolicyRuleOutstandingAction.COMPLETE_CALCULATION_SETUP
                        || outstanding == PolicyRuleOutstandingAction.ANSWER_CLARIFICATION);
        out.put("showChange", state == PolicyRuleLenderState.READY_TO_TEST
                || state == PolicyRuleLenderState.ACCEPTED_READY_TO_TEST
                || state == PolicyRuleLenderState.PRODUCTION_BLOCKED
                || state == PolicyRuleLenderState.PRODUCTION_READY
                || state == PolicyRuleLenderState.READY_FOR_CONFIRMATION);
        // Contradiction guards — UI must never invent opposite badges
        out.put("forbidNeedsInputWhenAcceptedReady",
                state == PolicyRuleLenderState.ACCEPTED_READY_TO_TEST
                        || state == PolicyRuleLenderState.READY_TO_TEST
                        || state == PolicyRuleLenderState.PRODUCTION_READY);
        out.put("forbidAcceptedBadgeWhenNeedsInput", state == PolicyRuleLenderState.NEEDS_INPUT);
        return out;
    }

    static PolicyRuleLenderState deriveState(Facts f) {
        if (!f.includedExecutable()) {
            return PolicyRuleLenderState.NOT_APPLICABLE;
        }
        if (!f.dataAvailableForPolicyDesign()) {
            return PolicyRuleLenderState.DATA_NOT_AVAILABLE;
        }
        if (!f.parameterResolved()
                || f.calculationRequired()
                || f.businessClarificationRequired()
                || !f.authoringComplete()) {
            return PolicyRuleLenderState.NEEDS_INPUT;
        }
        // Setup complete
        if (!f.ruleAccepted()) {
            return PolicyRuleLenderState.READY_FOR_CONFIRMATION;
        }
        if (f.productionReady()) {
            return PolicyRuleLenderState.PRODUCTION_READY;
        }
        if (f.policyTestReady()) {
            // Accepted + testable; production still blocked is a nuance, not a second primary state
            return PolicyRuleLenderState.ACCEPTED_READY_TO_TEST;
        }
        if (f.runtimeReady()) {
            return PolicyRuleLenderState.PRODUCTION_BLOCKED;
        }
        // Accepted but still not test-ready should not happen if facts consistent — treat as needs input
        return PolicyRuleLenderState.NEEDS_INPUT;
    }

    static PolicyRuleOutstandingAction deriveOutstanding(Facts f, PolicyRuleLenderState state) {
        return switch (state) {
            case NOT_APPLICABLE -> PolicyRuleOutstandingAction.NONE;
            case DATA_NOT_AVAILABLE -> PolicyRuleOutstandingAction.NONE;
            case NEEDS_INPUT -> {
                if (!f.parameterResolved()) {
                    yield PolicyRuleOutstandingAction.RESOLVE_PARAMETER;
                }
                if (f.businessClarificationRequired()) {
                    yield PolicyRuleOutstandingAction.ANSWER_CLARIFICATION;
                }
                if (f.calculationRequired()) {
                    yield PolicyRuleOutstandingAction.COMPLETE_CALCULATION_SETUP;
                }
                yield PolicyRuleOutstandingAction.COMPLETE_CALCULATION_SETUP;
            }
            case READY_FOR_CONFIRMATION -> {
                if (f.knownExistingCalculationPending()) {
                    yield PolicyRuleOutstandingAction.ACCEPT_EXISTING_CALCULATION;
                }
                if (f.newCalculationProposalPending()) {
                    yield PolicyRuleOutstandingAction.APPROVE_NEW_CALCULATION;
                }
                yield PolicyRuleOutstandingAction.ACCEPT_RULE;
            }
            case READY_TO_TEST, ACCEPTED_READY_TO_TEST, PRODUCTION_BLOCKED ->
                    PolicyRuleOutstandingAction.TEST;
            case PRODUCTION_READY -> PolicyRuleOutstandingAction.NONE;
        };
    }

    static String lenderLabel(PolicyRuleLenderState state, Facts f) {
        return ruleLifecycleLabel(state, f);
    }

    /**
     * Rule lifecycle only — never use this string to claim calculation setup is required
     * when the parameter is already executable.
     */
    static String ruleLifecycleLabel(PolicyRuleLenderState state, Facts f) {
        return switch (state) {
            case NEEDS_INPUT -> {
                if (f.calculationRequired()) {
                    yield "Needs review";
                }
                if (!f.parameterResolved()) {
                    yield "Needs your input";
                }
                yield "Needs review";
            }
            case READY_FOR_CONFIRMATION -> "Needs review";
            case READY_TO_TEST -> "Ready to test";
            case ACCEPTED_READY_TO_TEST -> "Accepted · Ready to test";
            case DATA_NOT_AVAILABLE -> "Data not available";
            case PRODUCTION_BLOCKED -> "Ready to test · Production setup pending";
            case PRODUCTION_READY -> "Production ready";
            case NOT_APPLICABLE -> "Not applicable";
        };
    }

    static String statusChip(PolicyRuleLenderState state, Facts f) {
        return switch (state) {
            case NEEDS_INPUT -> {
                if (f.calculationRequired()) {
                    // Chip reflects outstanding setup; parameter axis still owns "Calculation needs setup"
                    yield "Needs review";
                }
                if (!f.parameterResolved()) {
                    yield "Needs your input";
                }
                yield "Needs review";
            }
            case READY_FOR_CONFIRMATION -> "Needs review";
            case READY_TO_TEST -> "Ready to test";
            case ACCEPTED_READY_TO_TEST -> "Accepted";
            case DATA_NOT_AVAILABLE -> "Unavailable";
            case PRODUCTION_BLOCKED -> "Ready to test";
            case PRODUCTION_READY -> "Accepted";
            case NOT_APPLICABLE -> f.ruleAccepted() ? "Accepted" : "Needs review";
        };
    }

    static String primaryActionLabel(PolicyRuleOutstandingAction a, Facts f) {
        return switch (a) {
            case RESOLVE_PARAMETER -> "Resolve parameter";
            case COMPLETE_CALCULATION_SETUP -> "Complete setup";
            case ANSWER_CLARIFICATION -> "Answer question";
            case ACCEPT_RULE -> "Accept";
            case ACCEPT_EXISTING_CALCULATION -> "Accept";
            case APPROVE_NEW_CALCULATION -> "Use this calculation";
            case TEST -> "Test";
            case CHANGE_CALCULATION -> "Change";
            case CREATE_SEPARATE_PARAMETER -> "Create separate parameter";
            case NONE -> null;
        };
    }

    static List<String> secondaryActions(PolicyRuleLenderState state, Facts f) {
        List<String> out = new ArrayList<>();
        if (state == PolicyRuleLenderState.READY_FOR_CONFIRMATION
                || state == PolicyRuleLenderState.READY_TO_TEST
                || state == PolicyRuleLenderState.ACCEPTED_READY_TO_TEST
                || state == PolicyRuleLenderState.PRODUCTION_BLOCKED
                || state == PolicyRuleLenderState.PRODUCTION_READY) {
            out.add("Change");
        }
        return out;
    }

    public static Facts factsFromCard(Map<String, Object> card, Map<String, Object> meta) {
        boolean included = !Boolean.FALSE.equals(card.get("includedForActivation"));
        if (card.containsKey("includedForActivation")) {
            included = Boolean.TRUE.equals(card.get("includedForActivation"));
        }
        String disposition = String.valueOf(meta.getOrDefault("disposition", ""));
        boolean ruleAccepted = "ACCEPTED".equalsIgnoreCase(disposition)
                || "EDITED".equalsIgnoreCase(disposition);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operands = card.get("operands") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();

        boolean unresolved = operands.stream().anyMatch(o -> Boolean.TRUE.equals(o.get("unresolved")));
        boolean unavailable = operands.stream().anyMatch(o -> Boolean.TRUE.equals(o.get("unavailable")));
        boolean calcRequired = operands.stream().anyMatch(o -> Boolean.TRUE.equals(o.get("calculationRequired")));
        boolean calcDefined = operands.stream().anyMatch(o -> Boolean.TRUE.equals(o.get("calculationDefined")))
                || (!calcRequired && operands.stream().anyMatch(o ->
                String.valueOf(o.getOrDefault("availabilityLabel", "")).toLowerCase(Locale.ROOT)
                        .contains("derived")));
        if (!calcRequired && !operands.isEmpty()) {
            calcDefined = true;
        }
        boolean calcValidated = operands.stream().anyMatch(o -> {
            String st = String.valueOf(o.getOrDefault("calculationDefinitionStatus", ""));
            return "TESTED".equals(st) || "PRODUCTION_READY".equals(st) || "DEFINED".equals(st);
        }) || (calcDefined && !calcRequired);

        // Execution capability — spine only for every required operand; never catalogue implemented
        List<String> operandIds = new ArrayList<>();
        for (Map<String, Object> o : operands) {
            Object pid = o.get("canonicalParameterId");
            if (pid == null) {
                pid = o.get("parameterId");
            }
            if (pid == null) {
                pid = o.get("resolvedParameterId");
            }
            if (pid != null && !String.valueOf(pid).isBlank()) {
                operandIds.add(String.valueOf(pid).trim());
            }
        }
        boolean spineOperandsCapable;
        if (operands.isEmpty()) {
            spineOperandsCapable = false;
        } else if (operandIds.size() < operands.stream()
                .filter(o -> !Boolean.TRUE.equals(o.get("unresolved"))).count()) {
            // Some resolved-looking operands lack an id — not test-ready
            spineOperandsCapable = false;
        } else if (operandIds.isEmpty()) {
            spineOperandsCapable = false;
        } else {
            spineOperandsCapable = true;
            for (String id : operandIds) {
                if (!ExecutionCapabilityAuthority.hasExecutionCapability(id, EvaluationMode.POLICY_TEST)) {
                    spineOperandsCapable = false;
                    break;
                }
            }
        }

        boolean authoringComplete = !Boolean.FALSE.equals(card.get("authoringComplete"));
        if (card.containsKey("authoringComplete")) {
            authoringComplete = Boolean.TRUE.equals(card.get("authoringComplete"));
        }

        boolean policyTestReady = spineOperandsCapable && !unresolved && authoringComplete;
        // Runtime / production: spine workflow/underwriting; production cert not established
        boolean runtimeReady = operandIds.stream().allMatch(id ->
                ExecutionCapabilityAuthority.hasExecutionCapability(id, EvaluationMode.W6_ACQUISITION)
                        || ExecutionCapabilityAuthority.hasExecutionCapability(id, EvaluationMode.UNDERWRITING));
        if (operandIds.isEmpty()) {
            runtimeReady = false;
        }
        boolean productionReady = false;

        boolean knownExisting = operands.stream().anyMatch(o ->
                Boolean.TRUE.equals(o.get("knownExistingCalculation"))
                        || (o.get("howCalculated") != null
                        && !Boolean.TRUE.equals(o.get("calculationRequired"))
                        && String.valueOf(o.getOrDefault("availabilityLabel", ""))
                        .toLowerCase(Locale.ROOT).contains("derived")));

        return new Facts(
                ruleAccepted,
                !unresolved,
                calcRequired && !spineOperandsCapable,
                calcDefined || !calcRequired || spineOperandsCapable,
                calcValidated || !calcRequired || spineOperandsCapable,
                false,
                !unavailable,
                policyTestReady,
                runtimeReady,
                productionReady,
                included,
                authoringComplete,
                knownExisting && !ruleAccepted && !calcRequired,
                false
        );
    }

    /** Helper for inventory / tests — spine POLICY_TEST for one id. */
    public static boolean parameterPolicyTestCapable(String canonicalParameterId) {
        return CanonicalParameterCapabilityProjection.project(canonicalParameterId)
                .get("policyTestReady") instanceof Boolean b && b;
    }
}
