package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardAnalyticsResponse {

    private long totalApplications;
    private long activePipeline;
    private long approvedCount;
    private long rejectedCount;
    private long disbursedCount;
    private long todayApplications;

    private BigDecimal totalDisbursedAmount;
    private BigDecimal totalRequestedAmount;
    private BigDecimal averageLoanSize;

    private double approvalRate;
    private double rejectionRate;
    private double conversionRate;

    private List<StatusCount> statusDistribution;
    private List<ProductCount> productDistribution;
    private List<BorrowerTypeCount> borrowerTypeDistribution;
    private List<MonthlyTrend> monthlyTrends;

    @Data
    @Builder
    public static class StatusCount {
        private String status;
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    public static class ProductCount {
        private String product;
        private long count;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    public static class BorrowerTypeCount {
        private String borrowerType;
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    public static class MonthlyTrend {
        private String month;
        private long applications;
        private long approved;
        private long disbursed;
        private BigDecimal disbursedAmount;
    }
}
