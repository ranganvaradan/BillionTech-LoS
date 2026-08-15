package com.los.core.requirement;

/**
 * W3/W4 stub retained for explicit construction in unit tests only.
 * Production planning uses {@link PolicyDrivenDataRequirementPlanner}.
 */
public class StubDataRequirementPlanner implements DataRequirementPlanner {

    @Override
    public RequirementPlanEntity plan(PlanInputs inputs) {
        throw new UnsupportedOperationException(
                "StubDataRequirementPlanner is not registered. Use PolicyDrivenDataRequirementPlanner (W4).");
    }
}
