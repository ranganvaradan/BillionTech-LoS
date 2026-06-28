package com.los.core.service.assignment;

import com.los.core.service.credit.CreditControlKeys;
import com.los.core.model.entity.AssignmentRuleSet;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.core.model.entity.UserRoleMapping;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.AssignmentRuleSetRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.UserRoleMappingRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentRuleApplicationServiceTest {

    @Mock
    private AssignmentRuleSetRepository ruleRepo;
    @Mock
    private UserRoleMappingRepository mappingRepo;
    @Mock
    private LosUserRepository userRepo;

    private AssignmentRuleApplicationService service;
    private final EffectiveUnderwritingContext ctx =
            new EffectiveUnderwritingContext(700, true, null, null, "MH", "Mumbai", "a", "b", "c", java.util.Map.of());

    @BeforeEach
    void setUp() {
        service = new AssignmentRuleApplicationService(
                ruleRepo, mappingRepo, userRepo, new AssignmentScopeMatcher());
    }

    @Test
    void resolvesUserFromMappingWhenNoExplicitUserOnRule() {
        UUID uId = UUID.randomUUID();
        AssignmentRuleSet r = rule("R1", "CREDIT_MANAGER", null);
        when(ruleRepo.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(r));
        when(mappingRepo.findByActiveIsTrueAndLosRoleOrderByPriorityDesc("CREDIT_MANAGER"))
                .thenReturn(List.of(UserRoleMapping.builder()
                        .id(UUID.randomUUID())
                        .userId(uId)
                        .losRole("CREDIT_MANAGER")
                        .active(true)
                        .priority(10)
                        .build()));
        when(userRepo.findById(uId)).thenReturn(Optional.of(
                LosUser.builder().id(uId).name("Casey").active(true).build()));

        LoanApplication app = app();
        service.applyAfterUnderwriting(app, ctx);

        assertEquals(uId, app.getAssignedTo());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) app.getFinancialInfo()
                .get(CreditControlKeys.ASSIGNMENT_INFO);
        assertNotNull(info);
        assertEquals("Casey", info.get("assignedUserName"));
        String reason = (String) info.get("assignmentReason");
        assertNotNull(reason);
    }

    @Test
    void leavesUnassignedWhenNoMappingMatch() {
        AssignmentRuleSet r = rule("R1", "ACCOUNTS", null);
        when(ruleRepo.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(r));
        when(mappingRepo.findByActiveIsTrueAndLosRoleOrderByPriorityDesc("ACCOUNTS"))
                .thenReturn(List.of());

        LoanApplication app = app();
        service.applyAfterUnderwriting(app, ctx);

        assertNull(app.getAssignedTo());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) app.getFinancialInfo()
                .get(CreditControlKeys.ASSIGNMENT_INFO);
        assertEquals("Unassigned", info.get("assignedUserName"));
    }

    @Test
    void explicitUserIdStillSetsAssignedTo() {
        UUID explicit = UUID.randomUUID();
        AssignmentRuleSet r = rule("R1", "CREDIT_OFFICER", explicit);
        when(ruleRepo.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                any(), any()))
                .thenReturn(List.of(r));
        when(userRepo.findById(explicit))
                .thenReturn(Optional.of(LosUser.builder()
                        .id(explicit)
                        .name("Pat")
                        .active(true)
                        .build()));

        LoanApplication app = app();
        service.applyAfterUnderwriting(app, ctx);

        assertEquals(explicit, app.getAssignedTo());
    }

    @Test
    void higherPriorityMappingWinsWhenWildcardAndSpecificBothMatch() {
        UUID uWildcard = UUID.randomUUID();
        UUID uSpecific = UUID.randomUUID();
        AssignmentRuleSet r = rule("R1", "CREDIT_MANAGER", null);
        when(ruleRepo.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(r));
        when(mappingRepo.findByActiveIsTrueAndLosRoleOrderByPriorityDesc("CREDIT_MANAGER"))
                .thenReturn(List.of(
                        UserRoleMapping.builder()
                                .id(UUID.randomUUID())
                                .userId(uWildcard)
                                .losRole("CREDIT_MANAGER")
                                .loanProduct(null)
                                .active(true)
                                .priority(100)
                                .build(),
                        UserRoleMapping.builder()
                                .id(UUID.randomUUID())
                                .userId(uSpecific)
                                .losRole("CREDIT_MANAGER")
                                .loanProduct("PERSONAL_LOAN")
                                .active(true)
                                .priority(50)
                                .build()
                ));
        when(userRepo.findById(uWildcard))
                .thenReturn(Optional.of(LosUser.builder()
                        .id(uWildcard)
                        .name("Wide")
                        .active(true)
                        .build()));

        LoanApplication application = app();
        service.applyAfterUnderwriting(application, ctx);

        assertEquals(uWildcard, application.getAssignedTo());
    }

    @Test
    void specificProductMappingWinsOverLowerPriorityWildcard() {
        UUID uWildcard = UUID.randomUUID();
        UUID uSpecific = UUID.randomUUID();
        AssignmentRuleSet r = rule("R1", "CREDIT_MANAGER", null);
        when(ruleRepo.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(r));
        when(mappingRepo.findByActiveIsTrueAndLosRoleOrderByPriorityDesc("CREDIT_MANAGER"))
                .thenReturn(List.of(
                        UserRoleMapping.builder()
                                .id(UUID.randomUUID())
                                .userId(uSpecific)
                                .losRole("CREDIT_MANAGER")
                                .loanProduct("PERSONAL_LOAN")
                                .active(true)
                                .priority(100)
                                .build(),
                        UserRoleMapping.builder()
                                .id(UUID.randomUUID())
                                .userId(uWildcard)
                                .losRole("CREDIT_MANAGER")
                                .loanProduct(null)
                                .active(true)
                                .priority(50)
                                .build()
                ));
        when(userRepo.findById(uSpecific))
                .thenReturn(Optional.of(LosUser.builder()
                        .id(uSpecific)
                        .name("Narrow")
                        .active(true)
                        .build()));

        LoanApplication application = app();
        service.applyAfterUnderwriting(application, ctx);

        assertEquals(uSpecific, application.getAssignedTo());
    }

    private static AssignmentRuleSet rule(String name, String role, UUID userId) {
        return AssignmentRuleSet.builder()
                .id(UUID.randomUUID())
                .name(name)
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .assignedRole(role)
                .assignedUserId(userId)
                .priority(100)
                .active(true)
                .build();
    }

    private static LoanApplication app() {
        LoanApplication a = new LoanApplication();
        a.setStatus(ApplicationStatus.UNDERWRITING);
        a.setBorrowerType(BorrowerType.INDIVIDUAL);
        a.setLoanProduct("PERSONAL_LOAN");
        a.setRequestedAmount(new BigDecimal("100000"));
        a.setTenureMonths(12);
        a.setPersonalInfo(new java.util.HashMap<>(Map.of("state", "MH", "city", "Mumbai")));
        a.setFinancialInfo(new java.util.HashMap<>());
        return a;
    }
}
