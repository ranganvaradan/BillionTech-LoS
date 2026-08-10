package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

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
    /** True when at least one visible invoice belongs to a program that allows borrower payments. */
    boolean paymentEnabled;
    String hdfcAccountNo;
    String hdfcIfscCode;
    List<Map<String, Object>> paymentEligibility;
    /** Set only after POST /invoices create — PLP invoice id for follow-up actions (e.g. copy upload). */
    String createdInvoiceId;
    List<BorrowerInvoiceItemResponse> invoices;
    List<BorrowerInvoiceLoanResponse> loans;
}
