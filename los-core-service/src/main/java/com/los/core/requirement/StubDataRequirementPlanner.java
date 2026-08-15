package com.los.core.requirement;

import org.springframework.stereotype.Component;

/**
 * W3 stub — Policy inventory → plan generation is deferred to W4.
 */
@Component
public class StubDataRequirementPlanner implements DataRequirementPlanner {

    @Override
    public RequirementPlanEntity plan(PlanInputs inputs) {
        throw new UnsupportedOperationException(
                "W4: Policy Parameter Inventory → DataRequirementPlanner.plan() not implemented in W3. "
                        + "Use RequirementPlanService.createPlan with explicit ItemSpecs for staging/tests.");
    }
}
