package com.los.core.model.enums;

/**
 * Distinguishes borrower loan origination from anchor (invoice discounting) onboarding.
 * Workflows are resolved per (borrowerType, loanProduct, intakeSegment).
 */
public enum IntakeSegment {
    BORROWER,
    ANCHOR
}
