package com.los.core.creditintelligence.decisionpolicy;

/**
 * Distinguishes NBFC-editable policy requirements from platform-controlled guardrails.
 * Stored in metadata only — PLATFORM_GUARDRAIL must never become editable solely via Studio UI.
 */
public enum PolicyGuardrailClass {
    NBFC_CONFIGURABLE,
    PLATFORM_GUARDRAIL,
    /** Repository evidence does not yet classify the control. */
    UNRESOLVED;

    public boolean isEditableByNbfcPolicyAuthor() {
        return this == NBFC_CONFIGURABLE;
    }

    public static PolicyGuardrailClass fromMetadata(Object raw) {
        if (raw == null) {
            return UNRESOLVED;
        }
        String s = String.valueOf(raw).trim().toUpperCase();
        try {
            return PolicyGuardrailClass.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return UNRESOLVED;
        }
    }
}
