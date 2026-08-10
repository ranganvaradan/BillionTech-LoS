package com.los.core.service.kyc;

import com.los.core.model.enums.StepOutcome;
import com.los.core.service.workflow.intake.KycStepIntakeCatalog;

import java.util.*;

/**
 * Evaluates workflow {@code mandatoryFieldGroups} with {@code logic: ANY} for KYC execution.
 */
public final class KycMandatoryGroupEvaluator {

    private KycMandatoryGroupEvaluator() {
    }

    public record AnyGroup(List<String> steps) {
    }

    public static List<AnyGroup> anyGroups(Map<String, Object> intakeConfig) {
        if (intakeConfig == null) {
            return List.of();
        }
        Object raw = intakeConfig.get("mandatoryFieldGroups");
        if (!(raw instanceof List<?> groups)) {
            return List.of();
        }
        List<AnyGroup> out = new ArrayList<>();
        for (Object item : groups) {
            if (!(item instanceof Map<?, ?> group)) {
                continue;
            }
            if (!"ANY".equalsIgnoreCase(stringValue(group.get("logic")))) {
                continue;
            }
            if (group.get("steps") instanceof List<?> steps) {
                List<String> members = steps.stream()
                        .map(KycMandatoryGroupEvaluator::stringValue)
                        .map(String::toUpperCase)
                        .filter(s -> !s.isBlank())
                        .toList();
                if (!members.isEmpty()) {
                    out.add(new AnyGroup(members));
                }
            }
        }
        return out;
    }

    public static Set<String> stepsInAnyGroups(Map<String, Object> intakeConfig) {
        Set<String> out = new HashSet<>();
        for (AnyGroup g : anyGroups(intakeConfig)) {
            out.addAll(g.steps());
        }
        return out;
    }

    public static boolean shouldHaltOnMandatoryFailure(String stepName, Map<String, Object> intakeConfig) {
        String s = stepName != null ? stepName.toUpperCase() : "";
        return !stepsInAnyGroups(intakeConfig).contains(s);
    }

    /**
     * When a step belongs to an ANY mandatory group, skip execution if the applicant did not supply
     * that step's identity input (another group member may satisfy the requirement).
     */
    public static boolean shouldSkipForMissingPayload(
            String stepName,
            Map<String, Object> intakeConfig,
            Map<String, Object> payload) {
        String s = stepName != null ? stepName.toUpperCase() : "";
        if (!stepsInAnyGroups(intakeConfig).contains(s)) {
            return false;
        }
        return !KycStepIntakeCatalog.hasExecutionPayload(stepName, payload);
    }

    /**
     * True when mandatory KYC requirements are not satisfied (ungrouped failures or unsatisfied ANY groups).
     */
    public static boolean hasMandatoryFailure(
            Map<String, StepOutcome> effectiveByStep,
            List<Map<String, Object>> workflowSteps,
            Map<String, Object> intakeConfig) {
        Set<String> grouped = stepsInAnyGroups(intakeConfig);
        Set<String> satisfiedGroups = new HashSet<>();

        for (AnyGroup group : anyGroups(intakeConfig)) {
            boolean anySuccess = false;
            boolean anyMandatory = false;
            for (String member : group.steps()) {
                if (!KycIdentityWorkflow.isKycIdentitySubStepName(member)) {
                    continue;
                }
                Boolean mandatory = mandatoryForStep(workflowSteps, member);
                if (mandatory == null || !mandatory) {
                    continue;
                }
                anyMandatory = true;
                StepOutcome o = effectiveByStep.get(member);
                if (o == StepOutcome.SUCCESS) {
                    anySuccess = true;
                    break;
                }
            }
            if (!anyMandatory) {
                satisfiedGroups.add(String.join(",", group.steps()));
                continue;
            }
            if (anySuccess) {
                satisfiedGroups.add(String.join(",", group.steps()));
            }
        }

        for (Map<String, Object> step : workflowSteps) {
            String stepName = stringValue(step.get("step")).toUpperCase();
            if (stepName.isBlank()) {
                continue;
            }
            if (!KycIdentityWorkflow.isKycIdentitySubStepName(stepName)) {
                continue;
            }
            if (!boolValue(step.getOrDefault("mandatory", true))) {
                continue;
            }
            if (grouped.contains(stepName)) {
                boolean groupSatisfied = anyGroups(intakeConfig).stream()
                        .filter(g -> g.steps().contains(stepName))
                        .anyMatch(g -> satisfiedGroups.contains(String.join(",", g.steps())));
                if (!groupSatisfied) {
                    StepOutcome o = effectiveByStep.get(stepName);
                    if (o == StepOutcome.FAILURE) {
                        // wait for group evaluation below
                        continue;
                    }
                    if (o != StepOutcome.SUCCESS) {
                        return true;
                    }
                }
                continue;
            }
            StepOutcome o = effectiveByStep.get(stepName);
            if (o == StepOutcome.FAILURE || o == null || o == StepOutcome.PENDING
                    || o == StepOutcome.ERROR || o == StepOutcome.MANUAL_REVIEW) {
                if (o == StepOutcome.FAILURE) {
                    return true;
                }
                if (o == null || o == StepOutcome.PENDING || o == StepOutcome.ERROR || o == StepOutcome.MANUAL_REVIEW) {
                    return true;
                }
            }
        }

        for (AnyGroup group : anyGroups(intakeConfig)) {
            if (satisfiedGroups.contains(String.join(",", group.steps()))) {
                continue;
            }
            boolean anyMandatory = false;
            boolean anySuccess = false;
            boolean allFailedOrMissing = true;
            for (String member : group.steps()) {
                if (!KycIdentityWorkflow.isKycIdentitySubStepName(member)) {
                    continue;
                }
                Boolean mandatory = mandatoryForStep(workflowSteps, member);
                if (mandatory == null || !mandatory) {
                    continue;
                }
                anyMandatory = true;
                StepOutcome o = effectiveByStep.get(member);
                if (o == StepOutcome.SUCCESS) {
                    anySuccess = true;
                    allFailedOrMissing = false;
                    break;
                }
                if (o == null || o == StepOutcome.PENDING || o == StepOutcome.ERROR || o == StepOutcome.MANUAL_REVIEW) {
                    allFailedOrMissing = false;
                }
            }
            if (!anyMandatory) {
                continue;
            }
            if (!anySuccess && allFailedOrMissing) {
                return true;
            }
            if (!anySuccess) {
                return true;
            }
        }
        return false;
    }

    private static Boolean mandatoryForStep(List<Map<String, Object>> workflowSteps, String stepName) {
        for (Map<String, Object> step : workflowSteps) {
            if (stepName.equalsIgnoreCase(stringValue(step.get("step")))) {
                return boolValue(step.getOrDefault("mandatory", true));
            }
        }
        return null;
    }

    private static String stringValue(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static boolean boolValue(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(stringValue(o));
    }
}
