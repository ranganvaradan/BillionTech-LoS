package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class RegulatoryReportResponse {

    private String reportType;
    private String period;
    private Instant generatedAt;
    private DigitalLendingCompliance digitalLending;
    private KycCompliance kyc;
    private DpdAnalysis dpdAnalysis;

    @Data
    @Builder
    public static class DigitalLendingCompliance {
        private long totalDigitalLoans;
        private long kfsIssued;
        private long kfsSigned;
        private long coolingOffComplied;
        private long esignCompleted;
        private double digitalComplianceRate;
        private String rbiCircularRef;
    }

    @Data
    @Builder
    public static class KycCompliance {
        private long totalKycInitiated;
        private long kycCompleted;
        private long kycFailed;
        private long aadhaarVerified;
        private long panVerified;
        private long cKycVerified;
        private long faceMatchCompleted;
        private double kycCompletionRate;
    }

    @Data
    @Builder
    public static class DpdAnalysis {
        private long totalActiveLoans;
        private long dpd0;
        private long dpd1to30;
        private long dpd31to60;
        private long dpd61to90;
        private long dpd90plus;
        private long npaCount;
        private BigDecimal totalNpaAmount;
        private double npaPercentage;
    }
}
