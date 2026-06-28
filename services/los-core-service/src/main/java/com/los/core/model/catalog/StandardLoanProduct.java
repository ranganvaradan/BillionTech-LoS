package com.los.core.model.catalog;

import java.util.List;
import java.util.Set;

/**
 * Canonical loan product codes (DB / API) — one master list for the LOS catalog.
 * Display names belong in the UI; never persist labels as {@code loan_product}.
 */
public final class StandardLoanProduct {

    public static final String PERSONAL_LOAN = "PERSONAL_LOAN";
    public static final String BUSINESS_TERM_LOAN = "BUSINESS_TERM_LOAN";
    public static final String BUSINESS_WC_OD = "BUSINESS_WC_OD";
    public static final String BUSINESS_WC_INVOICE_DISCOUNTING = "BUSINESS_WC_INVOICE_DISCOUNTING";
    public static final String TERM_LOAN = "TERM_LOAN";
    public static final String LOAN_AGAINST_PROPERTY = "LOAN_AGAINST_PROPERTY";
    public static final String LOAN_AGAINST_SECURITIES = "LOAN_AGAINST_SECURITIES";
    public static final String LOAN_AGAINST_GOLD = "LOAN_AGAINST_GOLD";

    public static final List<String> ALL = List.of(
            PERSONAL_LOAN,
            BUSINESS_TERM_LOAN,
            BUSINESS_WC_OD,
            BUSINESS_WC_INVOICE_DISCOUNTING,
            TERM_LOAN,
            LOAN_AGAINST_PROPERTY,
            LOAN_AGAINST_SECURITIES,
            LOAN_AGAINST_GOLD);

    public static final Set<String> SECURED = Set.of(
            LOAN_AGAINST_PROPERTY, LOAN_AGAINST_SECURITIES, LOAN_AGAINST_GOLD);

    /** Loan products permitted for {@link com.los.core.model.enums.IntakeSegment#ANCHOR} intake. */
    public static final Set<String> ANCHOR_INTAKE_ALLOWED_PRODUCTS = Set.of(BUSINESS_WC_INVOICE_DISCOUNTING);

    private StandardLoanProduct() {
    }
}
