package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.model.entity.LoanApplication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps real LoanApplication fields to applicability query dimensions.
 * Only uses fields present on the LOS application model.
 */
public final class ApplicationPolicyQueryFactory {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private ApplicationPolicyQueryFactory() {}

    /**
     * Business / evaluation date rule (P1):
     * 1. Explicit evaluationAsOf if provided
     * 2. Else application submittedAt (Asia/Kolkata calendar date)
     * 3. Else application createdAt
     * Never LocalDate.now() for resolution of historical apps.
     */
    public static LocalDate resolveEvaluationBusinessDate(LoanApplication app, LocalDate evaluationAsOf) {
        if (evaluationAsOf != null) {
            return evaluationAsOf;
        }
        if (app == null) {
            return null;
        }
        if (app.getSubmittedAt() != null) {
            return LocalDate.ofInstant(app.getSubmittedAt(), BUSINESS_ZONE);
        }
        if (app.getCreatedAt() != null) {
            return LocalDate.ofInstant(app.getCreatedAt(), BUSINESS_ZONE);
        }
        return null;
    }

    public static ApplicationPolicyQuery fromLoanApplication(LoanApplication app, LocalDate evaluationAsOf) {
        LocalDate asOf = resolveEvaluationBusinessDate(app, evaluationAsOf);
        String product = app == null || app.getLoanProduct() == null ? null
                : app.getLoanProduct().trim().toUpperCase();
        String borrowerType = app == null || app.getBorrowerType() == null ? null
                : app.getBorrowerType().name();
        String segment = app == null || app.getIntakeSegment() == null ? null
                : app.getIntakeSegment().name();
        BigDecimal amount = app == null ? null : app.getRequestedAmount();
        return new ApplicationPolicyQuery(
                app == null || app.getApplicationNumber() == null
                        ? (app == null || app.getId() == null ? null : app.getId().toString())
                        : app.getApplicationNumber(),
                product,
                null, // facilityType not on LoanApplication
                segment,
                borrowerType,
                null, // secured/unsecured not on LoanApplication
                null, // programScheme — subProgramId exists but code not mapped without join
                amount,
                asOf
        );
    }

    public static Map<String, Object> evidence(LoanApplication app, LocalDate asOf, Map<String, Object> resolveResult) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("product", app == null ? null : app.getLoanProduct());
        e.put("requestedAmount", app == null ? null : app.getRequestedAmount());
        e.put("borrowerType", app == null || app.getBorrowerType() == null ? null : app.getBorrowerType().name());
        e.put("intakeSegment", app == null || app.getIntakeSegment() == null ? null : app.getIntakeSegment().name());
        e.put("evaluationBusinessDate", asOf == null ? null : asOf.toString());
        e.put("dateRule", "evaluationAsOf > submittedAt > createdAt (Asia/Kolkata); never wall-clock now()");
        e.put("resolverOutcome", resolveResult == null ? null : resolveResult.get("outcome"));
        e.put("reason", resolveResult == null ? null : resolveResult.get("reason"));
        e.put("unsupportedDimensions", Map.of(
                "facilityType", "not on loan_applications",
                "securedUnsecured", "not on loan_applications",
                "programScheme", "requires sub_program join — not used in P1"));
        e.put("mappingReliability", PolicyScopeSupport.mappingReliability());
        e.put("nullMatchSafety",
                "Constrained policy dimensions do not match when the application attribute is absent");
        return e;
    }
}
