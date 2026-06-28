package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Aggregate payload for the borrower Invoice discounting page: the borrower's PLP invoices plus their
 * invoice-discounting loans. {@code available} is false (with {@code message}) when invoice discounting cannot
 * be served — PLP disabled, the borrower not yet synced to PLP, or PLP unreachable — so the UI shows a calm
 * empty state instead of an error.
 */
@Value
@Builder
public class BorrowerInvoiceDiscountingResponse {
    boolean available;
    String message;
    /** SMART_COLLECT or PAYU_PG when available. */
    String paymentMethod;
    List<BorrowerInvoiceItemResponse> invoices;
    List<BorrowerInvoiceLoanResponse> loans;
}
