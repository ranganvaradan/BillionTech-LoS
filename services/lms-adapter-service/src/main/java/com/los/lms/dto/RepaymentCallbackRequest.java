package com.los.lms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RepaymentCallbackRequest {

    private String lmsReferenceId;
    private String applicationNumber;
    private int installmentNumber;
    private BigDecimal paidAmount;
    private LocalDate paymentDate;
    private String paymentMode;
    private String utrNumber;
    private String status;
}
