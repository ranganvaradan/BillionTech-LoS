package com.los.core.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assignment and user-role mapping roles (fixed set).
 */
public enum AssignmentRole {
    SALES_OFFICER,
    RELATIONSHIP_MANAGER,
    CREDIT_OFFICER,
    CREDIT_MANAGER,
    OPERATIONS,
    ADMINISTRATOR,
    ACCOUNTS;

    private static final Map<String, AssignmentRole> ALIASES = Arrays.stream(values())
            .collect(Collectors.toMap(
                    e -> e.name().toLowerCase(Locale.ROOT),
                    e -> e,
                    (a, b) -> a));

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static AssignmentRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (AssignmentRole e : values()) {
            if (e.name().equalsIgnoreCase(v)) {
                return e;
            }
        }
        // tolerate legacy: CREDIT_MANAGER from IAM-style
        return ALIASES.get(v.toLowerCase(Locale.ROOT));
    }

    public static boolean isValid(String value) {
        return fromString(value) != null;
    }

    public String getDisplayLabel() {
        return switch (this) {
            case SALES_OFFICER -> "Sales Officer";
            case RELATIONSHIP_MANAGER -> "Relationship Manager";
            case CREDIT_OFFICER -> "Credit Officer";
            case CREDIT_MANAGER -> "Credit Manager";
            case OPERATIONS -> "Operations";
            case ADMINISTRATOR -> "Administrator";
            case ACCOUNTS -> "Accounts";
        };
    }
}
