package com.los.core.model.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manual credit layer — does not overwrite bureau pull / KYC provider rows; merged under {@code financialInfo.creditControl}.
 */
@Data
public class ManualCreditInputsRequest {

    private String panName;
    private String panStatus;
    private String aadhaarName;
    private String aadhaarStatus;
    private Boolean mobileVerified;
    private Integer manualBureauScore;
    private String manualBureauRemarks;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyObligation;
    /** Optional manual GST return / assessed income. */
    private BigDecimal gstIncome;
    /** Declared or assessed income from bank statements. */
    private BigDecimal bankStatementIncome;
    private BigDecimal averageBankBalance;
    /** If set, overrides income-based obligation ratio. */
    private BigDecimal obligationRatio;
    /** Eligible monthly EMI / obligation total (tagged manual; does not replace provider obligation). */
    private BigDecimal emiObligation;
    private BigDecimal propertyValue;
    private BigDecimal ltv;
    private Integer businessVintageMonths;
    private String industryRisk;
    private String repaymentHistory;
    private BigDecimal ebitdaProxy;
    private BigDecimal leverageRatio;
    private String state;
    private String city;
    private String creditRemarks;
    /** When using manual KYC source: PASS or FAIL */
    private String manualKycOutcome;

    private DecisionSources decisionSources;

    @Data
    public static class DecisionSources {
        private String bureauScoreSource;
        private String incomeSource;
        private String kycSource;
    }

    /** Optional: tag each field with source for audit (defaults to MANUAL for provided fields) */
    private Map<String, String> fieldSourceHints;

    /** Document ids in the local store linked to this manual credit packet (evidence). */
    private List<UUID> supportingDocumentIds;

    /** Bank statement analytics (scorecard BANK_STATEMENT source). */
    private BigDecimal avgDailyBalance3m;
    private Integer avgMonthlyTransactions3m;
    private BigDecimal avgMonthlySettlements3m;
    private Integer monthlyTransactions3m;
    private Integer inwardChequeReturns3m;
    private BigDecimal avgDailySettlements3m;
    private Integer noOfTxns60days;
    private Integer txnMth1;
    private Integer txnMth2;
    private Integer txnMth3;

    /** GST statement metrics (scorecard GST_STATEMENT source). */
    private BigDecimal avgGmv3m;
    private Integer active90days;
    private BigDecimal annualGstTurnover;

    /** ITR metrics (scorecard ITR source). */
    private BigDecimal itrIncome;
    private BigDecimal pat;
    private BigDecimal interestCoverage;
    private BigDecimal debtToEquity;
    private BigDecimal ebitda;
    private BigDecimal debtService;

    /** Bank statement analytics extensions (SCF / invoice discounting). */
    private BigDecimal annualBankingTurnover;
    private BigDecimal bankingTurnoverPctGst;
    private BigDecimal abbObligationMultiple;
    private BigDecimal ccUtilisationPct;
    private Integer chequeBounces12m;
    private Integer chequeBounces3m;

    /** Bureau extensions. */
    private Integer liveUnsecuredLoanCount;
    private Integer bureauEnquiries3m;
    private String ntcFlag;

    /** Other manual underwriting fields (scorecard OTHER source). */
    private String residenceOwned;
    private BigDecimal residenceStability;
    private BigDecimal businessStability;
    private String existingLoanTrackRecordAll;
    private String existingLoanTrackRecord15d;
    private String qrTxnEDI;
    private String eligibleOnePointFiveX;
    private BigDecimal existingFbLimits;
    private BigDecimal existingNfbLimits;
    private BigDecimal tolTnw;
    private String officeOwned;

    /** Scorecard-context computed / declared ratios. */
    private BigDecimal dscr;

    /**
     * Additional scorecard parameters (custom OTHER rows) — keys match scorecard parameter codes.
     */
    private Map<String, Object> scorecardMetrics;
}
