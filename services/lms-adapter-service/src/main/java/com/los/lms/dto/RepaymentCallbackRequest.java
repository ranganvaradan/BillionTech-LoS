package com.los.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentCallbackRequest {

    private String lmsReferenceId;
    private String applicationNumber;
    private int installmentNumber;
    private BigDecimal paidAmount;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;
    private LocalDate paymentDate;
    private String paymentMode;
    private String utrNumber;
    private String status;
}
