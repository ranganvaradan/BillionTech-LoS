package com.los.core.architecture.regression;

/**
 * Wave 0 assertion classification — records CURRENT reality; does not declare defects "correct".
 */
public enum Wave0Classification {
    /** Convergence must not break this. */
    MUST_PRESERVE,
    /** Honest capture of a known defect / dual authority. */
    KNOWN_GAP,
    /** Baseline records current value; a later wave may change it intentionally. */
    EXPECTED_TO_CHANGE
}
