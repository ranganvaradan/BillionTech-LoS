package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class MisReportResponse {

    private String reportType;
    private String period;
    private Instant generatedAt;
    private Summary summary;
    private List<ReportRow> rows;

    @Data
    @Builder
    public static class Summary {
        private long totalApplications;
        private long approved;
        private long rejected;
        private long disbursed;
        private long pending;
        private BigDecimal totalDisbursedAmount;
        private BigDecimal totalRequestedAmount;
        private double approvalRate;
        private double avgProcessingDays;
    }

    @Data
    @Builder
    public static class ReportRow {
        private String applicationNumber;
        private String borrowerName;
        private String borrowerType;
        private String loanProduct;
        private BigDecimal requestedAmount;
        private BigDecimal approvedAmount;
        private String status;
        private String kycStatus;
        private Instant createdAt;
        private Instant submittedAt;
        private int processingDays;
    }
}
