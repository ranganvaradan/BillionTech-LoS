package com.los.core.creditintelligence.policystudio.parameters.lifecycle;

/**
 * What the lender must do next — never a vague "Needs your input" without an action.
 */
public enum PolicyRuleOutstandingAction {
    NONE,
    RESOLVE_PARAMETER,
    COMPLETE_CALCULATION_SETUP,
    ANSWER_CLARIFICATION,
    ACCEPT_RULE,
    ACCEPT_EXISTING_CALCULATION,
    APPROVE_NEW_CALCULATION,
    TEST,
    CHANGE_CALCULATION,
    CREATE_SEPARATE_PARAMETER
}
