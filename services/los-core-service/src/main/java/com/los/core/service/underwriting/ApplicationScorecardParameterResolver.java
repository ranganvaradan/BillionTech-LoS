package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.service.workflow.intake.IntakeOptionCatalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Resolves APPLICATION-sourced scorecard parameters from loan application data.
 * Occupation / loan purpose return codes for string matching on the scorecard;
 * numeric scores are authored only on scorecard rows / parameterDefs.
 */
public final class ApplicationScorecardParameterResolver {

    private ApplicationScorecardParameterResolver() {
    }

    public static BigDecimal resolve(String param, LoanApplication app, Map<String, Object> intakeConfig) {
        if (param == null || app == null) {
            return null;
        }
        Map<String, Object> personal = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        BigDecimal standard = switch (param.trim().toUpperCase()) {
            case "AGE", "APPLICANT_AGE", "AGE_YEARS" -> ageYears(personal);
            // OCCUPATION / LOAN_PURPOSE are string codes — resolved via resolveString()
            default -> null;
        };
        if (standard != null) {
            return standard;
        }
        return decimalValue(resolveRawField(param, app));
    }

    public static String resolveString(String param, LoanApplication app) {
        if (param == null || app == null) {
            return null;
        }
        Map<String, Object> personal = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        String standard = switch (param.trim().toUpperCase()) {
            case "OCCUPATION" -> blankToNull(IntakeOptionCatalog.resolveOccupationCode(personal));
            case "LOAN_PURPOSE", "LOANPURPOSE", "PURPOSE" ->
                    blankToNull(IntakeOptionCatalog.resolveLoanPurposeCode(personal));
            default -> null;
        };
        if (standard != null) {
            return standard;
        }
        Object raw = resolveRawField(param, app);
        return raw == null ? null : blankToNull(String.valueOf(raw).trim());
    }

    public static BigDecimal ageYears(Map<String, Object> personal) {
        String dobStr = stringValue(personal.get("dateOfBirth"));
        if (dobStr.isBlank()) {
            return null;
        }
        try {
            LocalDate dob = LocalDate.parse(dobStr);
            int age = Period.between(dob, LocalDate.now()).getYears();
            return BigDecimal.valueOf(age);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveRawField(String param, LoanApplication app) {
        String key = param != null ? param.trim() : "";
        if (key.isBlank() || app == null) {
            return null;
        }
        Map<String, Object> personal = app.getPersonalInfo() != null ? app.getPersonalInfo() : Map.of();
        Map<String, Object> business = app.getBusinessInfo() != null ? app.getBusinessInfo() : Map.of();
        Map<String, Object> financial = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        Object custom = personal.get("customFields");
        if (custom instanceof Map<?, ?> customMap) {
            for (Map.Entry<?, ?> entry : customMap.entrySet()) {
                if (key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                    return entry.getValue();
                }
            }
        }
        for (Map<String, Object> bag : new Map[]{personal, business, financial}) {
            for (Map.Entry<String, Object> entry : bag.entrySet()) {
                if (key.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static BigDecimal decimalValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            String s = String.valueOf(raw).trim();
            if (s.isBlank()) {
                return null;
            }
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringValue(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
