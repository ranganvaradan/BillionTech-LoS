package com.los.core.customercategory;

import com.los.core.exception.ForbiddenException;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;

import java.util.Locale;
import java.util.Set;

/**
 * Maker/checker/activator roles aligned with Scorecard governance.
 */
public final class ConfigGovernanceRoles {

    public static final Set<String> MAKER_ROLES = Set.of(
            "CREDIT_MANAGER", "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN");
    public static final Set<String> CHECKER_ROLES = Set.of(
            "POLICY_CHECKER", "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN", "RISK_MANAGER");
    public static final Set<String> ACTIVATOR_ROLES = Set.of(
            "CREDIT_MANAGER", "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN");

    private ConfigGovernanceRoles() {}

    public static void requireMaker(Actor actor) {
        requireRole(actor, MAKER_ROLES, "Credit Manager / Administrator required for maker actions");
    }

    public static void requireChecker(Actor actor) {
        requireRole(actor, CHECKER_ROLES, "Policy Checker / Approver role required");
    }

    public static void requireActivator(Actor actor) {
        requireRole(actor, ACTIVATOR_ROLES, "Credit Manager / Administrator required to activate");
    }

    public static void requireAuthenticated(Actor actor) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank()) {
            throw new ForbiddenException("Authentication required (X-User-Id)");
        }
        if (actor.role() == null || actor.role().isBlank()) {
            throw new ForbiddenException("Authentication required (X-User-Role)");
        }
    }

    public static void forbidSelfApproval(Actor makerSubmitter, Actor checker) {
        if (makerSubmitter == null || checker == null) {
            return;
        }
        String m = normalizeId(makerSubmitter);
        String c = normalizeId(checker);
        if (m != null && c != null && m.equalsIgnoreCase(c)) {
            throw CustomerCategoryValidator.biz(
                    "Maker-checker: submitter cannot approve their own configuration",
                    "SELF_APPROVAL_FORBIDDEN",
                    java.util.Map.of("submitter", m, "checker", c));
        }
    }

    private static String normalizeId(Actor a) {
        if (a.userId() != null && !a.userId().isBlank()) {
            return a.userId().trim();
        }
        if (a.displayName() != null && !a.displayName().isBlank()) {
            return a.displayName().trim();
        }
        return null;
    }

    private static void requireRole(Actor actor, Set<String> allowed, String message) {
        requireAuthenticated(actor);
        String role = actor.role().trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(role)) {
            throw new ForbiddenException(message);
        }
    }
}
