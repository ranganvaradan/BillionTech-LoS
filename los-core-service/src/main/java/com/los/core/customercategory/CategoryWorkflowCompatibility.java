package com.los.core.customercategory;

import com.los.core.model.entity.WorkflowConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * W2 — Workflow compatibility with a Customer Category proposition.
 * Separate from Policy scope compatibility: "Can this Workflow process this proposition?"
 */
public final class CategoryWorkflowCompatibility {

    public static final String STATUS_COMPATIBLE = "COMPATIBLE";
    public static final String STATUS_INCOMPATIBLE = "INCOMPATIBLE";

    public static final String WORKFLOW_SCOPE_INCOMPATIBLE = "WORKFLOW_SCOPE_INCOMPATIBLE";
    public static final String ENTITY_TYPE_NOT_COVERED = "ENTITY_TYPE_NOT_COVERED";
    public static final String PRODUCT_NOT_COVERED = "PRODUCT_NOT_COVERED";
    public static final String CUSTOMER_ROLE_NOT_COVERED = "CUSTOMER_ROLE_NOT_COVERED";
    public static final String CREDIT_VINTAGE_NOT_COVERED = "CREDIT_VINTAGE_NOT_COVERED";
    public static final String WORKFLOW_NOT_ACTIVE = "WORKFLOW_NOT_ACTIVE";

    private CategoryWorkflowCompatibility() {}

    public record Result(
            String status,
            boolean compatible,
            List<String> reasons,
            List<Check> checks,
            String scopeSummary
    ) {}

    public record Check(String code, String label, boolean ok, String detail) {}

    public static Result evaluate(CustomerCategoryEntity category, WorkflowConfig wf) {
        String role = category.getIntakeSegment();
        String entity = category.getBorrowerType();
        String product = category.getLoanProduct();
        String creditVintage = category.getCreditVintage();
        return evaluate(role, entity, product, creditVintage, wf);
    }

    public static Result evaluate(
            String customerRole, String entityType, String loanProduct, String creditVintage, WorkflowConfig wf) {
        List<Check> checks = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        boolean governed = wf != null && (
                wf.isActive()
                        || "ACTIVE".equals(wf.resolvedPublicationStatus())
                        || "SUPERSEDED".equals(wf.resolvedPublicationStatus()));
        checks.add(new Check(
                "WORKFLOW_ACTIVE",
                "Workflow Version is governed for Category use (ACTIVE or SUPERSEDED)",
                governed,
                wf == null ? "Missing" : ("publication=" + wf.resolvedPublicationStatus()
                        + " active=" + wf.isActive())));
        if (!governed) {
            reasons.add(WORKFLOW_NOT_ACTIVE);
        }

        boolean entityOk = wf != null && equalsNorm(entityType, wf.getBorrowerType());
        checks.add(new Check(
                ENTITY_TYPE_NOT_COVERED,
                "Entity Type / borrowerType matches Workflow",
                entityOk,
                wf == null ? "Missing"
                        : "Category=" + entityType + " Workflow=" + wf.getBorrowerType()));
        if (!entityOk) {
            reasons.add(ENTITY_TYPE_NOT_COVERED);
        }

        boolean productOk = wf != null && equalsNorm(loanProduct, wf.getLoanProduct());
        checks.add(new Check(
                PRODUCT_NOT_COVERED,
                "Loan Product matches Workflow",
                productOk,
                wf == null ? "Missing"
                        : "Category=" + loanProduct + " Workflow=" + wf.getLoanProduct()));
        if (!productOk) {
            reasons.add(PRODUCT_NOT_COVERED);
        }

        boolean roleOk = wf != null && equalsNorm(customerRole, wf.getIntakeSegment());
        checks.add(new Check(
                CUSTOMER_ROLE_NOT_COVERED,
                "Customer Role / intakeSegment matches Workflow",
                roleOk,
                wf == null ? "Missing"
                        : "Category=" + customerRole + " Workflow=" + wf.getIntakeSegment()));
        if (!roleOk) {
            reasons.add(CUSTOMER_ROLE_NOT_COVERED);
        }

        boolean vintageOk = wf != null && equalsNorm(creditVintage, wf.getCreditVintage());
        checks.add(new Check(
                CREDIT_VINTAGE_NOT_COVERED,
                "Credit Vintage matches Workflow",
                vintageOk,
                wf == null ? "Missing"
                        : "Category=" + creditVintage + " Workflow=" + wf.getCreditVintage()));
        if (!vintageOk) {
            reasons.add(CREDIT_VINTAGE_NOT_COVERED);
        }

        boolean compatible = reasons.isEmpty();
        String summary = wf == null ? "No Workflow"
                : "Workflow " + nullToEmpty(wf.getName())
                + " v" + wf.getVersion()
                + " · " + nullToEmpty(wf.getBorrowerType())
                + " / " + nullToEmpty(wf.getLoanProduct())
                + " / " + nullToEmpty(wf.getIntakeSegment())
                + " · " + wf.resolvedPublicationStatus();
        return new Result(
                compatible ? STATUS_COMPATIBLE : STATUS_INCOMPATIBLE,
                compatible,
                List.copyOf(reasons),
                List.copyOf(checks),
                summary);
    }

    /**
     * ANY on either side is a wildcard (mirrors {@link com.los.core.customercategory.selection
     * .CustomerCategoryEligibilityService}'s dimMatch semantics) — otherwise exact match.
     * Every existing Workflow defaults to creditVintage=ANY (V155 backfill), so without this,
     * every Category scoped to a specific vintage would be reported incompatible with every
     * Workflow in the system.
     */
    private static boolean equalsNorm(String a, String b) {
        if (MatchWildcard.isAny(a) || MatchWildcard.isAny(b)) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean sameKey(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }
}
