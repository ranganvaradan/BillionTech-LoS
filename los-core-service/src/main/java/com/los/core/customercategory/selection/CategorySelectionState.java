package com.los.core.customercategory.selection;

/**
 * Application Category selection stage state (eligibility ≠ selection).
 */
public enum CategorySelectionState {
    NO_ELIGIBLE_CATEGORY,
    AUTO_SINGLE_MATCH,
    DISAMBIGUATION_REQUIRED,
    EXPLICIT_PROPOSITION_SELECTION_REQUIRED,
    CATEGORY_SELECTED
}
