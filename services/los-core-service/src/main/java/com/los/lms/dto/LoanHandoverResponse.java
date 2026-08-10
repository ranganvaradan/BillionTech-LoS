package com.los.lms.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class LoanHandoverResponse {

    private UUID handoverId;
    private String applicationNumber;
    private String lmsReferenceId;
    private String status;
    private String message;
    private LocalDate firstEmiDate;
    private BigDecimal emiAmount;
    private int totalEmis;
}
