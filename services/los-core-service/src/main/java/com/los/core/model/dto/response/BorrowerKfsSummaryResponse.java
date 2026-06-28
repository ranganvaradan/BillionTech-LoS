package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Safe subset of KFS for borrower read-only use. */
@Value
@Builder
public class BorrowerKfsSummaryResponse {
    String kfsId;
    String version;
    BigDecimal sanctionedAmount;
    BigDecimal interestRate;
    BigDecimal apr;
    Integer tenureMonths;
    BigDecimal emiAmount;
    BigDecimal totalRepayment;
    String status;
    boolean signPending;
    boolean coolingOffComplete;
}
