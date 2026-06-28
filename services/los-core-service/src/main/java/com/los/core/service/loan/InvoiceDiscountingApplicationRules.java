package com.los.core.service.loan;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;

/**
 * Shared rules for invoice-discounting anchor vs borrower application flows.
 */
public final class InvoiceDiscountingApplicationRules {

    private InvoiceDiscountingApplicationRules() {
    }

    public static boolean isInvoiceDiscounting(LoanApplication app) {
        return app != null
                && StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct());
    }

    public static boolean isAnchorFlow(LoanApplication app) {
        return isInvoiceDiscounting(app) && app.getIntakeSegment() == IntakeSegment.ANCHOR;
    }

    public static boolean isBorrowerFlow(LoanApplication app) {
        return isInvoiceDiscounting(app) && app.getIntakeSegment() != IntakeSegment.ANCHOR;
    }

    /** Anchor onboarding ends at sanction — no CAM, eSign, disbursement, LMS loan, or KFS. */
    public static boolean skipsCamEsignDisbursement(LoanApplication app) {
        return isAnchorFlow(app);
    }

    /** Invoice discounting flows do not open an LMS loan at sanction. */
    public static boolean skipsLmsAtSanction(LoanApplication app) {
        return isAnchorFlow(app) || isBorrowerFlow(app);
    }

    /** Anchor sanction does not produce a KFS / terms PDF entity. */
    public static boolean skipsKfsAtSanction(LoanApplication app) {
        return isAnchorFlow(app);
    }

    /** @deprecated use {@link #skipsLmsAtSanction} and {@link #skipsKfsAtSanction} */
    @Deprecated
    public static boolean skipsLmsAndKfsAtSanction(LoanApplication app) {
        return skipsKfsAtSanction(app);
    }
}
