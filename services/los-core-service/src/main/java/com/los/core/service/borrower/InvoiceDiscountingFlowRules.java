package com.los.core.service.borrower;

/**
 * PLP invoice-discounting flow rules mirrored from the PLP borrower portal.
 */
final class InvoiceDiscountingFlowRules {

    private static final String FLOW_PURCHASE_BILL_DISCOUNTING = "PURCHASE_BILL_DISCOUNTING";
    private static final String FLOW_SALES_BILL_DISCOUNTING = "SALES_BILL_DISCOUNTING";
    private static final String FLOW_PURCHASE_ORDER_DISCOUNTING = "PURCHASE_ORDER_DISCOUNTING";

    private InvoiceDiscountingFlowRules() {
    }

    static boolean isPurchaseFlow(String flowType) {
        return flowType == null || flowType.isBlank() || FLOW_PURCHASE_BILL_DISCOUNTING.equalsIgnoreCase(flowType);
    }

    static boolean isSalesFlow(String flowType) {
        return FLOW_SALES_BILL_DISCOUNTING.equalsIgnoreCase(flowType);
    }

    static boolean isPurchaseOrderFlow(String flowType) {
        return FLOW_PURCHASE_ORDER_DISCOUNTING.equalsIgnoreCase(flowType);
    }

    static boolean isSellerInitiatedFlow(String flowType) {
        return isSalesFlow(flowType) || isPurchaseOrderFlow(flowType);
    }

    static boolean acceptable(String status, String flowType) {
        return isPurchaseFlow(flowType) && "ELIGIBLE".equalsIgnoreCase(status);
    }

    static boolean financeable(String status, String flowType) {
        if (status == null) {
            return false;
        }
        if ("FINANCING_REQUESTED".equalsIgnoreCase(status)) {
            return false;
        }
        if ("DISCOUNTED_EP".equalsIgnoreCase(status) || "SANCTIONED_EP".equalsIgnoreCase(status)) {
            return false;
        }
        if (isPurchaseFlow(flowType)) {
            return "BORROWER_ACCEPTED".equalsIgnoreCase(status) || "PARTIALLY_DISCOUNTED".equalsIgnoreCase(status);
        }
        if (isSellerInitiatedFlow(flowType)) {
            return "ELIGIBLE".equalsIgnoreCase(status) || "PARTIALLY_DISCOUNTED".equalsIgnoreCase(status);
        }
        return "BORROWER_ACCEPTED".equalsIgnoreCase(status) || "PARTIALLY_DISCOUNTED".equalsIgnoreCase(status);
    }

    static boolean earlyPayable(String status, String flowType, String isEarlyPayAllowed, String showEarlyPay) {
        return isSalesFlow(flowType)
                && "ELIGIBLE".equalsIgnoreCase(status)
                && "YES".equalsIgnoreCase(isEarlyPayAllowed)
                && "YES".equalsIgnoreCase(showEarlyPay);
    }

    /**
     * Borrower may delete SBD/PO invoices only, before finance is requested,
     * when the program allows invoice delete.
     */
    static boolean deletableByBorrower(String status, String flowType, boolean programInvoiceDeleteAllowed) {
        if (!programInvoiceDeleteAllowed || !isSellerInitiatedFlow(flowType)) {
            return false;
        }
        if (status == null || status.isBlank() || "FINANCING_REQUESTED".equalsIgnoreCase(status)) {
            return false;
        }
        return "UPLOADED".equalsIgnoreCase(status)
                || "VERIFIED".equalsIgnoreCase(status)
                || "ELIGIBLE".equalsIgnoreCase(status)
                || "BORROWER_ACCEPTED".equalsIgnoreCase(status)
                || "REJECTED".equalsIgnoreCase(status);
    }
}
