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
}
