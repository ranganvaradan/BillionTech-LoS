package com.los.core.service.assignment;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UserRoleMapping;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentScopeMatcherTest {

    private final AssignmentScopeMatcher matcher = new AssignmentScopeMatcher();

    @Test
    void mappingRespectsProductWhenSet() {
        UserRoleMapping m = UserRoleMapping.builder()
                .losRole("CREDIT_OFFICER")
                .userId(new java.util.UUID(0L, 1L))
                .loanProduct("Home")
                .active(true)
                .priority(0)
                .build();
        LoanApplication app = baseApp();
        app.setLoanProduct("PERSONAL_LOAN");
        assertFalse(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
    }

    @Test
    void mappingWithNullProductMatchesAllApplicationProducts() {
        UserRoleMapping m = UserRoleMapping.builder()
                .losRole("CREDIT_OFFICER")
                .userId(new java.util.UUID(0L, 1L))
                .loanProduct(null)
                .active(true)
                .priority(0)
                .build();
        LoanApplication app = baseApp();
        app.setLoanProduct("PERSONAL_LOAN");
        assertTrue(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
        app.setLoanProduct("Gold");
        assertTrue(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
    }

    @Test
    void mappingWithSpecificProductMatchesWhenEqual() {
        UserRoleMapping m = UserRoleMapping.builder()
                .losRole("CREDIT_OFFICER")
                .userId(new java.util.UUID(0L, 1L))
                .loanProduct("PERSONAL_LOAN")
                .active(true)
                .priority(0)
                .build();
        LoanApplication app = baseApp();
        app.setLoanProduct("PERSONAL_LOAN");
        assertTrue(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
        app.setLoanProduct("personal_loan");
        assertTrue(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
    }

    @Test
    void mappingMatchesAmountRange() {
        UserRoleMapping m = UserRoleMapping.builder()
                .losRole("CREDIT_OFFICER")
                .userId(new java.util.UUID(0L, 1L))
                .minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("50000"))
                .active(true)
                .priority(0)
                .build();
        LoanApplication app = baseApp();
        app.setRequestedAmount(new BigDecimal("30000"));
        assertTrue(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
        app.setRequestedAmount(new BigDecimal("9000"));
        assertFalse(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
    }

    @Test
    void mappingGeoRequiresState() {
        UserRoleMapping m = UserRoleMapping.builder()
                .losRole("CREDIT_OFFICER")
                .userId(new java.util.UUID(0L, 1L))
                .geography(Map.of("state", "KA"))
                .active(true)
                .priority(0)
                .build();
        LoanApplication app = baseApp();
        app.getPersonalInfo().put("state", "MH");
        assertFalse(matcher.matchesMapping(m, app, ctx("MH", "Pune")));
        app.getPersonalInfo().put("state", "KA");
        assertTrue(matcher.matchesMapping(m, app, ctx("KA", "Bengaluru")));
    }

    private static LoanApplication baseApp() {
        LoanApplication app = new LoanApplication();
        app.setStatus(ApplicationStatus.UNDERWRITING);
        app.setBorrowerType(BorrowerType.INDIVIDUAL);
        app.setLoanProduct("PERSONAL_LOAN");
        app.setRequestedAmount(new BigDecimal("200000"));
        app.setTenureMonths(12);
        app.setPersonalInfo(new java.util.HashMap<>(Map.of("state", "MH", "city", "Mumbai")));
        return app;
    }

    private static EffectiveUnderwritingContext ctx(String st, String city) {
        return new EffectiveUnderwritingContext(700, true, null, null, st, city, "x", "y", "z", java.util.Map.of());
    }
}
