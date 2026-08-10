package com.los.core.service.loan;

import com.los.core.exception.ForbiddenException;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowRoleGuard {

    private static final Set<String> MAKER_ROLES = Set.of(
            "CREDIT_OFFICER", "ADMIN", "ADMINISTRATOR", "RISK_MANAGER");
    /** Officers and managers who may edit CAM fields (not necessarily submit). */
    private static final Set<String> CAM_EDITOR_ROLES = Set.of(
            "CREDIT_OFFICER", "CREDIT_MANAGER", "ADMIN", "ADMINISTRATOR", "RISK_MANAGER");
    private static final Set<String> CHECKER_ROLES = Set.of(
            "CREDIT_MANAGER", "ADMIN", "ADMINISTRATOR", "RISK_MANAGER");
    private static final Set<String> L2_SANCTION_ROLES = Set.of(
            "CREDIT_MANAGER", "ADMIN", "ADMINISTRATOR", "RISK_MANAGER");

    /** Staff who create applications / notify borrower (intake owners). */
    private static final Set<String> INTAKE_ROLES = Set.of(
            "RELATIONSHIP_MANAGER", "ADMIN", "ADMINISTRATOR");
    private static final Set<String> RM_OR_ADMIN = Set.of(
            "RELATIONSHIP_MANAGER", "ADMIN", "ADMINISTRATOR");
    private static final Set<String> CO_OR_ADMIN = Set.of(
            "CREDIT_OFFICER", "ADMIN", "ADMINISTRATOR");

    private final LosUserRepository losUserRepository;

    public void requireMaker(UUID userId) {
        requireRole(userId, MAKER_ROLES, "Credit officer (maker) role required");
    }

    public void requireCamEditor(UUID userId) {
        requireRole(userId, CAM_EDITOR_ROLES, "CAM editor role required");
    }

    public void requireChecker(UUID userId) {
        requireRole(userId, CHECKER_ROLES, "Credit manager (checker) role required");
    }

    public void requireL2Sanction(UUID userId) {
        requireRole(userId, L2_SANCTION_ROLES, "Credit manager (L2) role required for sanction");
    }

    public void requireIntakeRole(String userRole) {
        requireRoleHeader(userRole, INTAKE_ROLES, "Relationship Manager or Admin role required for intake");
    }

    public void requireCreateApplicationRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return;
        }
        String role = userRole.trim().toUpperCase(Locale.ROOT);
        if ("BORROWER".equals(role) || INTAKE_ROLES.contains(role)) {
            return;
        }
        throw new ForbiddenException(
                "Create application requires Relationship Manager, Admin, or Borrower role");
    }

    public void requireRelationshipManagerOrAdmin(String userRole) {
        requireRoleHeader(userRole, RM_OR_ADMIN,
                "Relationship Manager or Admin role required");
    }

    public void requireCreditOfficerOrAdmin(String userRole) {
        requireRoleHeader(userRole, CO_OR_ADMIN,
                "Credit Officer or Admin role required");
    }

    private void requireRoleHeader(String userRole, Set<String> allowed, String message) {
        if (userRole == null || userRole.isBlank()) {
            return;
        }
        String role = userRole.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(role)) {
            throw new ForbiddenException(message);
        }
    }

    private void requireRole(UUID userId, Set<String> allowed, String message) {
        if (userId == null) {
            return;
        }
        LosUser user = losUserRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("User not found"));
        String role = user.getPrimaryLosRole() != null
                ? user.getPrimaryLosRole().trim().toUpperCase(Locale.ROOT)
                : "";
        if (!allowed.contains(role)) {
            throw new ForbiddenException(message);
        }
    }
}
