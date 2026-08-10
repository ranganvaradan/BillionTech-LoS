package com.los.plp.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreatePlpProgramRequest {

    @NotNull
    private UUID anchorId;

    @NotBlank
    private String programName;

    @NotBlank
    private String programType;

    private BigDecimal creditLimit;

    private BigDecimal interestRate;

    private Integer tenureDays;

    private String currency;

    private LocalDate validityStartDate;

    private LocalDate validityEndDate;

    private String subProgramCode;

    private String subProgramName;

    private BigDecimal subProgramLimit;

    /** PURCHASE_BILL_DISCOUNTING | SALES_BILL_DISCOUNTING (invoice discounting). */
    private String flowType;

    /** YES | NO — post invoice loans to Encore LMS when enabled on PLP. */
    private String lmsEntryIn;

    /** Encore product code when lmsEntryIn=YES. */
    private String encoreProductCode;

    /** Minimum borrower dependency on anchor (%). */
    private BigDecimal dependencyVintagePercent;

    /** Minimum anchor relationship vintage (months). */
    private Integer anchorRelationshipVintageMonths;
}
