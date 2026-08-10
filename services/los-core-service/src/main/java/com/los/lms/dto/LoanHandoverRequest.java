package com.los.lms.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class LoanHandoverRequest {

    private UUID applicationId;
    private String applicationNumber;
    private String borrowerName;
    private String borrowerType;
    private String loanProduct;
    private BigDecimal sanctionedAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal emiAmount;
    private Map<String, Object> borrowerDetails;
    private Map<String, Object> collateralDetails;
    private String productCode;

    /**
     * When set, sent as Encore {@code loanOdAccount.customerId1} (bl-core uses party / customer id).
     */
    private String encorePartyOrCustomerId;

    /** Overrides Encore {@code loanOdAccount.branchCode} when set (else admin branch from config). */
    private String encoreBranchCode;

    // ---- bl-core parity fields ----

    /** bl-core partner code for partner-specific product resolution. */
    private String partnerCode;

    /** Penal interest rate from product config (bl-core: loan.getOverDueInterestRt()). */
    private BigDecimal penalInterestRate;

    /** Tenure unit (Month, Quarter, Half Year, Year, Week, Day). */
    @Builder.Default
    private String tenureUnit = "Month";

    /** Number of installments (bl-core uses this for tenureMagnitude, not calendar months). */
    private Integer numberOfInstallments;

    /** Moratorium type (None, EmiHoliday, PrincipalHoliday, etc.). */
    @Builder.Default
    private String moratoriumType = "None";

    /** Moratorium period magnitude. */
    @Builder.Default
    private Integer moratoriumPeriodMagnitude = 0;

    /** Moratorium period unit. */
    @Builder.Default
    private String moratoriumPeriodUnit = "Month";

    /** Whether normal interest accrues during moratorium. */
    private boolean moratoriumNormalInterestRateApplicable;

    /** Interest rate during moratorium (null = same as normal). */
    private String moratoriumNormalInterestRate;

    /** Interest accrual calculation mode during moratorium. */
    private String moratoriumInterestAccrualCalculation;

    // ---- Co-lending fields ----
    private String colendingApplicable;
    private String colenderProductCode;
    private String colenderId;
    private String colenderLendingRatio;
    private String colenderNormalInterestRate;

    // ---- Disbursement control ----
    /** Disbursement date override (yyyy-MM-dd); null = today. */
    private String disbursementDate;

    /** User ID for Encore transaction posting (bl-core: logged-in user). */
    private String userId;

    /** Tranche ID for partial disbursement (bl-core: param6). */
    private String trancheId;
}
