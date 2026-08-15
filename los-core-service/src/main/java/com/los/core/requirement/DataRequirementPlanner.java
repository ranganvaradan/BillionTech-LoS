package com.los.core.requirement;

/**
 * Future Policy Parameter Inventory → RequirementPlan generator (W4).
 * <p>
 * W3 provides the contract only. Implementations must not execute sources or render UI.
 */
public interface DataRequirementPlanner {

    /**
     * Generate a RequirementPlan from Policy inventory + facts + workflow.
     * <p>
     * <b>W4:</b> Real Policy inventory → plan generation is not implemented in W3.
     *
     * @throws UnsupportedOperationException until W4 implements Policy-driven planning
     */
    RequirementPlanEntity plan(PlanInputs inputs);
}
