package com.los.core.security;

import com.los.core.exception.ForbiddenException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * LOS-LIVE-P0-CLOSURE-1 — fail-closed staff access for document/PII surfaces.
 * Uses existing X-User-Id / X-User-Role header convention (interim until JWT).
 */
@Service
@RequiredArgsConstructor
public class StaffAccessGuard {

    public static final Set<String> STAFF_ROLES = Set.of(
            "ADMINISTRATOR", "ADMIN", "CREDIT_MANAGER", "CREDIT_OFFICER",
            "RELATIONSHIP_MANAGER", "OPERATIONS", "RISK_MANAGER", "ACCOUNTS",
            "POLICY_CHECKER", "PLATFORM_ADMIN");

    public static final Set<String> PRIVILEGED_ROLES = Set.of(
            "ADMINISTRATOR", "ADMIN", "CREDIT_MANAGER", "RISK_MANAGER", "PLATFORM_ADMIN");

    private final LoanApplicationRepository loanApplicationRepository;

    public UUID requireStaffUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }

    public String requireStaffRole(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String role = roleHeader.trim().toUpperCase(Locale.ROOT);
        if ("BORROWER".equals(role)) {
            throw new ForbiddenException("Staff role required");
        }
        if (!STAFF_ROLES.contains(role)) {
            throw new ForbiddenException("Staff role required");
        }
        return role;
    }

    public void requireStaff(String userIdHeader, String roleHeader) {
        requireStaffUserId(userIdHeader);
        requireStaffRole(roleHeader);
    }

    public void requireAdmin(String userIdHeader, String roleHeader) {
        requireStaffUserId(userIdHeader);
        String role = requireStaffRole(roleHeader);
        if (!PRIVILEGED_ROLES.contains(role)) {
            throw new ForbiddenException("Administrator or Credit Manager role required");
        }
    }

    /**
     * Application-scoped document access. Missing or unauthorized → same 404 (no ID leakage).
     */
    public void assertCanAccessDocument(String userIdHeader, String roleHeader, Document doc) {
        UUID userId = requireStaffUserId(userIdHeader);
        String role = requireStaffRole(roleHeader);
        if (doc == null || doc.getApplicationId() == null) {
            throw new ResourceNotFoundException("Document not found");
        }
        if (PRIVILEGED_ROLES.contains(role) || "CREDIT_OFFICER".equals(role) || "OPERATIONS".equals(role)) {
            // Staff ops roles may access application documents in LOS workbench
            ensureApplicationExists(doc.getApplicationId());
            return;
        }
        LoanApplication app = loanApplicationRepository.findById(doc.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (userId.equals(app.getAssignedTo())) {
            return;
        }
        throw new ResourceNotFoundException("Document not found");
    }

    public void assertCanAccessApplication(String userIdHeader, String roleHeader, UUID applicationId) {
        UUID userId = requireStaffUserId(userIdHeader);
        String role = requireStaffRole(roleHeader);
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (PRIVILEGED_ROLES.contains(role) || "CREDIT_OFFICER".equals(role)
                || "OPERATIONS".equals(role) || "RELATIONSHIP_MANAGER".equals(role)) {
            return;
        }
        if (userId.equals(app.getAssignedTo())) {
            return;
        }
        throw new ResourceNotFoundException("Application not found");
    }

    private void ensureApplicationExists(UUID applicationId) {
        if (!loanApplicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("Document not found");
        }
    }
}
