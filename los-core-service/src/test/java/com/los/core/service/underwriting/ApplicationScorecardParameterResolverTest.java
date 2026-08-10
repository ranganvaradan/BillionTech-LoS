package com.los.core.service.underwriting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApplicationScorecardParameterResolverTest {

    @Test
    void resolvesAgeFromDateOfBirth() {
        String dob = LocalDate.now().minusYears(30).toString();
        BigDecimal age = ApplicationScorecardParameterResolver.ageYears(Map.of("dateOfBirth", dob));
        assertNotNull(age);
        assertEquals(30, age.intValue());
    }

    @Test
    void resolvesOccupationAsCodeString() {
        Map<String, Object> personal = Map.of("occupation", "SALARIED_GOVERNMENT");
        assertEquals(
                "SALARIED_GOVERNMENT",
                ApplicationScorecardParameterResolver.resolveString(
                        "OCCUPATION", appWithPersonal(personal)));
    }

    @Test
    void resolvesLoanPurposeAsCodeString() {
        Map<String, Object> personal = Map.of("loanPurpose", "DEBT_CONSOLIDATION");
        assertEquals(
                "DEBT_CONSOLIDATION",
                ApplicationScorecardParameterResolver.resolveString(
                        "LOAN_PURPOSE", appWithPersonal(personal)));
    }

    @Test
    void numericResolveDoesNotReturnOccupationScore() {
        assertNull(ApplicationScorecardParameterResolver.resolve(
                "OCCUPATION",
                appWithPersonal(Map.of("occupation", "SALARIED_GOVERNMENT")),
                Map.of()));
    }

    private static com.los.core.model.entity.LoanApplication appWithPersonal(Map<String, Object> personal) {
        com.los.core.model.entity.LoanApplication a = new com.los.core.model.entity.LoanApplication();
        a.setPersonalInfo(new java.util.HashMap<>(personal));
        return a;
    }
}
