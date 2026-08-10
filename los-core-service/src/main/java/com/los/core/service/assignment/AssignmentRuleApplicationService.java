package com.los.core.service.assignment;

import com.los.core.model.entity.AssignmentRuleSet;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.core.model.entity.UserRoleMapping;
import com.los.core.model.enums.AssignmentRole;
import com.los.core.repository.AssignmentRuleSetRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.UserRoleMappingRepository;
import com.los.core.service.credit.CreditControlKeys;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Picks a matching active assignment rule when an application is in underwriting (or pending manual).
 */
@Service
@RequiredArgsConstructor
public class AssignmentRuleApplicationService {

    private final AssignmentRuleSetRepository repository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final LosUserRepository losUserRepository;
    private final AssignmentScopeMatcher scopeMatcher;

    @Transactional
    public void applyAfterUnderwriting(LoanApplication app, EffectiveUnderwritingContext ctx) {
        if (app.getStatus() == null) {
            return;
        }
        List<AssignmentRuleSet> candidates = repository
                .findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        app.getBorrowerType().name(), app.getLoanProduct());
        for (AssignmentRuleSet r : candidates) {
            if (scopeMatcher.matchesRule(r, app, ctx)) {
                Resolution res = resolveAssignment(r, app, ctx);
                if (r.getAssignedUserId() != null) {
                    // Preserve legacy behavior: rule always sets queue owner when a user id is present.
                    app.setAssignedTo(r.getAssignedUserId());
                } else if (res.effectiveUserId() != null) {
                    app.setAssignedTo(res.effectiveUserId());
                }
                Map<String, Object> fi = app.getFinancialInfo() != null
                        ? new HashMap<>(app.getFinancialInfo())
                        : new HashMap<>();
                Map<String, Object> info = new HashMap<>();
                info.put("ruleId", r.getId().toString());
                info.put("ruleName", r.getName());
                info.put("assignmentRuleName", r.getName());
                String roleName = resolveRoleName(r.getAssignedRole());
                info.put("assignedRole", roleName);
                UUID idForInfo = r.getAssignedUserId() != null
                        ? r.getAssignedUserId()
                        : res.effectiveUserId();
                if (idForInfo != null) {
                    info.put("assignedUserId", idForInfo.toString());
                }
                if (res.userName() != null) {
                    info.put("assignedUserName", res.userName());
                } else if (idForInfo == null) {
                    info.put("assignedUserName", "Unassigned");
                }
                info.put("assignmentReason", res.assignmentReason());
                info.put("assignedAt", Instant.now().toString());
                fi.put(CreditControlKeys.ASSIGNMENT_INFO, info);
                app.setFinancialInfo(fi);
                return;
            }
        }
    }

    private String resolveRoleName(String stored) {
        AssignmentRole e = AssignmentRole.fromString(stored);
        if (e != null) {
            return e.getDisplayLabel();
        }
        return stored;
    }

    private Resolution resolveAssignment(AssignmentRuleSet rule, LoanApplication app, EffectiveUnderwritingContext ctx) {
        if (rule.getAssignedUserId() != null) {
            Optional<LosUser> u = losUserRepository.findById(rule.getAssignedUserId());
            if (u.isPresent()) {
                return new Resolution(
                        rule.getAssignedUserId(),
                        u.get().isActive() ? u.get().getName() : "Inactive user",
                        "Matched rule \'" + rule.getName() + "\'; user explicitly set on the rule."
                                + (u.get().isActive() ? "" : " (directory user is inactive)."));
            }
            return new Resolution(
                    rule.getAssignedUserId(),
                    "Unknown user",
                    "Matched rule \'" + rule.getName() + "\'; user id set on rule (not in LOS directory).");
        }
        return tryMappingFallback(
                rule,
                app,
                ctx,
                "Matched rule \'" + rule.getName() + "\'; no explicit user, resolving from role mappings…");
    }

    private Resolution tryMappingFallback(AssignmentRuleSet rule, LoanApplication app, EffectiveUnderwritingContext ctx,
            String preface) {
        String key = normalizeRoleKey(rule.getAssignedRole());
        if (key == null) {
            return new Resolution(null, null, preface + " invalid role. Unassigned.");
        }
        List<UserRoleMapping> cands = userRoleMappingRepository
                .findByActiveIsTrueAndLosRoleOrderByPriorityDesc(key);
        for (UserRoleMapping m : cands) {
            if (!scopeMatcher.matchesMapping(m, app, ctx)) {
                continue;
            }
            Optional<LosUser> u = losUserRepository.findById(m.getUserId());
            if (u.isEmpty() || !u.get().isActive()) {
                continue;
            }
            return new Resolution(
                    m.getUserId(),
                    u.get().getName(),
                    preface + " auto-selected user from mapping (priority " + m.getPriority() + ").");
        }
        return new Resolution(
                null,
                null,
                preface + " no eligible active user in user-role mappings for this segment. Unassigned.");
    }

    private String normalizeRoleKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        AssignmentRole e = AssignmentRole.fromString(raw);
        if (e != null) {
            return e.name();
        }
        return raw.trim();
    }

    private record Resolution(UUID effectiveUserId, String userName, String assignmentReason) {}
}
