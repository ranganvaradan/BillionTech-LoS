package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
@Builder
public class BorrowerProgramEnrollmentResponse {
    String subProgramId;
    String subProgramName;
    String subProgramCode;
    String subProgramStatus;
    String flowType;
    BigDecimal subProgramInterestRate;
    BigDecimal subProgramMarginPercent;
    Integer subProgramMaxTenureDays;

    String programId;
    String programName;
    String programCode;
    String programStatus;
    String productType;
    BigDecimal programLimit;
    BigDecimal programUtilizedLimit;
    BigDecimal programAvailableLimit;
    BigDecimal defaultInterestRate;
    BigDecimal programMarginPercent;
    Integer programMaxTenureDays;

    BigDecimal borrowerLimit;
    BigDecimal borrowerUtilizedLimit;
    BigDecimal borrowerAvailableLimit;
    String membershipStatus;

    Map<String, Object> programParameters;
    Map<String, Object> programConfig;
    String lmsEntryIn;
    String encoreProductCode;

    BigDecimal borrowerInterestRate;
    BigDecimal borrowerDiscountMarginPercent;
    Integer borrowerCreditPeriodDays;
    String borrowerDiscountHold;
    String borrowerPaymentMethod;
    BigDecimal borrowerOverdueInterestRate;
}
