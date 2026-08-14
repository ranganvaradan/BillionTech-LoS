package com.los.core.domain;

import com.los.core.customercategory.MatchWildcard;
import com.los.core.exception.BusinessRuleException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * STEP-1 terminology compatibility: resolve canonical Customer Role / Entity Type
 * from either new or transitional request fields without changing storage semantics.
 */
public final class CreditTerminologyCompatibility {

    public static final String CONFLICT_CUSTOMER_ROLE = "TERMINOLOGY_CONFLICT_CUSTOMER_ROLE";
    public static final String CONFLICT_ENTITY_TYPE = "TERMINOLOGY_CONFLICT_ENTITY_TYPE";

    private CreditTerminologyCompatibility() {}

    /**
     * Prefer {@code customerRole} when present; otherwise {@code intakeSegment}.
     * Both may be supplied if equal (case-insensitive) after normalisation.
     * {@code ANY} is allowed for category match dimensions.
     *
     * @return value to persist in {@code intake_segment}
     */
    public static String resolveCustomerRoleForStorage(String customerRole, String intakeSegment) {
        String canonical = normalizeDim(customerRole);
        String legacy = normalizeDim(intakeSegment);
        if (canonical == null && legacy == null) {
            return null;
        }
        if (canonical != null && legacy != null && !canonical.equalsIgnoreCase(legacy)) {
            throw conflict(
                    "customerRole and intakeSegment disagree",
                    CONFLICT_CUSTOMER_ROLE,
                    Map.of("customerRole", String.valueOf(customerRole), "intakeSegment", String.valueOf(intakeSegment)));
        }
        String chosen = canonical != null ? canonical : legacy;
        if (MatchWildcard.isAny(chosen)) {
            return MatchWildcard.ANY;
        }
        CustomerRole.fromIntakeSegmentValue(chosen);
        return chosen.toUpperCase(Locale.ROOT);
    }

    /**
     * Prefer {@code entityType} when present; otherwise {@code borrowerType}.
     *
     * @return value to persist in {@code borrower_type}
     */
    public static String resolveEntityTypeForStorage(String entityType, String borrowerType) {
        String canonical = normalizeDim(entityType);
        String legacy = normalizeDim(borrowerType);
        if (canonical == null && legacy == null) {
            return null;
        }
        if (canonical != null && legacy != null && !canonical.equalsIgnoreCase(legacy)) {
            throw conflict(
                    "entityType and borrowerType disagree",
                    CONFLICT_ENTITY_TYPE,
                    Map.of("entityType", String.valueOf(entityType), "borrowerType", String.valueOf(borrowerType)));
        }
        String chosen = canonical != null ? canonical : legacy;
        if (MatchWildcard.isAny(chosen)) {
            return MatchWildcard.ANY;
        }
        EntityType.fromBorrowerTypeValue(chosen);
        return chosen.toUpperCase(Locale.ROOT);
    }

    public static String toCustomerRoleAlias(String intakeSegmentStorage) {
        return intakeSegmentStorage;
    }

    public static String toEntityTypeAlias(String borrowerTypeStorage) {
        return borrowerTypeStorage;
    }

    public static boolean sameDim(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(normalizeDim(left), normalizeDim(right));
    }

    private static String normalizeDim(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (MatchWildcard.isAny(t)) {
            return MatchWildcard.ANY;
        }
        return t.toUpperCase(Locale.ROOT);
    }

    private static BusinessRuleException conflict(String message, String reason, Map<String, Object> context) {
        return new BusinessRuleException(message, reason, "FIX_CATEGORY_CONFIG",
                context == null ? Map.of() : new LinkedHashMap<>(context));
    }
}
