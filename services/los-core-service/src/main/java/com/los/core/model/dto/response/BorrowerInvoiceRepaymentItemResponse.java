package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** A single repayment row for an invoice-discounting loan. */
@Value
@Builder
public class BorrowerInvoiceRepaymentItemResponse {
    String repaymentId;
    String paidAt;
    BigDecimal amount;
    String reference;
    String source;
    String status;
    String paymentMode;
    String friendlySource;
}
