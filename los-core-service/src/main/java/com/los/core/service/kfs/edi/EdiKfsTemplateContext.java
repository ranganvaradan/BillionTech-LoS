package com.los.core.service.kfs.edi;

import java.math.BigDecimal;
import java.util.List;

/** Placeholder values for the BL sanction / KFS Word template. */
public record EdiKfsTemplateContext(
        String currentDate,
        String fullName,
        String address,
        String limitRs,
        String loanAmountRs,
        String loanAmountPlain,
        String loanAmtText,
        String interestRate,
        String tenor,
        String expiryDate,
        String appRefNo,
        String kfsExpDate,
        String firstRepaymentDate,
        String typeOfInstalment,
        String repaymentFrequency,
        String totalInterestRs,
        String netDisburseAmountRs,
        String totalAmountRs,
        String annualPercentage,
        String reducingBalanceBasics,
        String noOfInstalments,
        String amountOfEachInstalmentRs,
        String penalInterest,
        List<EdiKfsScheduleRow> scheduleRows) {}
