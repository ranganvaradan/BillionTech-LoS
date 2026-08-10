package com.los.core.creditintelligence.decisionpolicy.kyc;

import java.util.List;
import java.util.Map;

/**
 * Explicit Policy vs Workflow ownership boundary (KYC-2 documentation + tooling constants).
 *
 * <p>Policy Studio owns: what the NBFC requires (outcomes/conditions).
 * Workflows own: sequence of LOS work / which steps to execute.
 * Integrations own: primary/fallback providers.
 * Application orchestration owns: may the application proceed.
 *
 * <p>Policy MUST NOT select providers or enqueue workflow steps.
 */
public final class KycPolicyWorkflowBoundary {

    private KycPolicyWorkflowBoundary() {}

    public static final String POLICY_OWNS = "REQUIREMENT_AND_OUTCOME";
    public static final String WORKFLOW_OWNS = "EXECUTION_SEQUENCE";
    public static final String INTEGRATIONS_OWN = "PROVIDER_ROUTING";
    public static final String ORCHESTRATION_OWNS = "STAGE_GATE";

    /**
     * WorkflowConfig fields that currently embed NBFC business requirements —
     * migration candidates for later KYC phases (do not migrate in KYC-2).
     */
    public static List<Map<String, String>> workflowEmbeddedRequirementCandidates() {
        return List.of(
                Map.of(
                        "field", "steps[].mandatory",
                        "example", "PAN_VERIFY mandatory for product X",
                        "futureOwner", "Decision Policy KYC & Eligibility"),
                Map.of(
                        "field", "vkycTriggerCondition",
                        "example", "VKYC when amount exceeds threshold",
                        "futureOwner", "Decision Policy BOUNDARY_CONDITION"),
                Map.of(
                        "field", "intakeConfig.mandatoryFieldGroups",
                        "example", "ANY-of document groups",
                        "futureOwner", "Decision Policy INFORMATION_REQUIREMENT"),
                Map.of(
                        "field", "GST_ANALYSIS presence",
                        "example", "GST report required before underwriting",
                        "futureOwner", "Decision Policy + keep UW gate consumer"));
    }

    public static Map<String, Object> ownershipManifest() {
        return Map.of(
                "policyStudio", POLICY_OWNS,
                "workflows", WORKFLOW_OWNS,
                "integrations", INTEGRATIONS_OWN,
                "applicationOrchestration", ORCHESTRATION_OWNS,
                "policySelectsProvider", false,
                "policyEnqueuesWorkflowStep", false,
                "kyc2ChangesProductionUwGate", false,
                "kyc2AutoStartsUnderwriting", false,
                "allowCanonicalAuthority", false);
    }
}
