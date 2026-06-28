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
        if (isPurchaseFlow(flowType)) {
            return "BORROWER_ACCEPTED".equalsIgnoreCase(status) || "PARTIALLY_DISCOUNTED".equalsIgnoreCase(status);
        }
        if (isSellerInitiatedFlow(flowType)) {
            return "ELIGIBLE".equalsIgnoreCase(status) || "PARTIALLY_DISCOUNTED".equalsIgnoreCase(status);
        }
        return "BORROWER_ACCEPTED".equalsIgnoreCase(status) || "PARTIALLY_DISCOUNTED".equalsIgnoreCase(status);
    }
}
