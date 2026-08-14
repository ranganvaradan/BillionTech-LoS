package com.los.core.domain;

import com.los.core.model.enums.BorrowerType;

/**
 * Canonical Entity Type (design lock).
 * Transitional storage/API still uses {@link BorrowerType} / {@code borrowerType}.
 * LLP / PRIVATE_LIMITED / PUBLIC_LIMITED are reserved for a later expansion step.
 */
public enum EntityType {
    INDIVIDUAL,
    PROPRIETOR,
    PARTNERSHIP,
    COMPANY;

    public static EntityType fromBorrowerType(BorrowerType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case INDIVIDUAL -> INDIVIDUAL;
            case PROPRIETOR -> PROPRIETOR;
            case PARTNERSHIP -> PARTNERSHIP;
            case COMPANY -> COMPANY;
        };
    }

    public static EntityType fromBorrowerTypeValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return fromBorrowerType(BorrowerType.valueOf(raw.trim().toUpperCase()));
    }

    public BorrowerType toBorrowerType() {
        return switch (this) {
            case INDIVIDUAL -> BorrowerType.INDIVIDUAL;
            case PROPRIETOR -> BorrowerType.PROPRIETOR;
            case PARTNERSHIP -> BorrowerType.PARTNERSHIP;
            case COMPANY -> BorrowerType.COMPANY;
        };
    }

    /** Storage / transitional API value (same strings as BorrowerType). */
    public String storageValue() {
        return name();
    }
}
