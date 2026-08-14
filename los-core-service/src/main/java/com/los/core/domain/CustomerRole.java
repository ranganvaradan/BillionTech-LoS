package com.los.core.domain;

import com.los.core.model.enums.IntakeSegment;

/**
 * Canonical Customer Role (design lock).
 * Transitional storage/API still uses {@link IntakeSegment} / {@code intakeSegment}.
 */
public enum CustomerRole {
    BORROWER,
    ANCHOR;

    public static CustomerRole fromIntakeSegment(IntakeSegment segment) {
        if (segment == null) {
            return null;
        }
        return switch (segment) {
            case BORROWER -> BORROWER;
            case ANCHOR -> ANCHOR;
        };
    }

    public static CustomerRole fromIntakeSegmentValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return fromIntakeSegment(IntakeSegment.valueOf(raw.trim().toUpperCase()));
    }

    public IntakeSegment toIntakeSegment() {
        return switch (this) {
            case BORROWER -> IntakeSegment.BORROWER;
            case ANCHOR -> IntakeSegment.ANCHOR;
        };
    }

    /** Storage / transitional API value (same strings as IntakeSegment). */
    public String storageValue() {
        return name();
    }
}
