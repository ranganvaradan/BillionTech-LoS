package com.los.lms.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RepaymentScheduleResponse {

    private String applicationNumber;
    private String lmsReferenceId;
    private BigDecimal sanctionedAmount;
    private BigDecimal interestRate;
    private int tenureMonths;
    private BigDecimal totalInterest;
    private BigDecimal totalPayable;
    private List<RepaymentScheduleEntry> schedule;
}
