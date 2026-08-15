package com.los.core.requirement;

/**
 * Future Policy Parameter Inventory → RequirementPlan generator.
 * <p>
 * W4: {@link PolicyDrivenDataRequirementPlanner} implements Policy graph → plan generation.
 * Implementations must not execute sources or render UI.
 */
public interface DataRequirementPlanner {

    /**
     * Generate a RequirementPlan from Policy inventory + facts + workflow metadata.
     * Planning only — no Bureau/AA/GST/OCR/KYC/Policy/Scorecard execution.
     */
    RequirementPlanEntity plan(PlanInputs inputs);
}
