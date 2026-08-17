package com.los.core.creditintelligence.bureau.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ci_bureau_report_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBureauReportSummary {

    @Id
    @Column(name = "bureau_report_id", nullable = false)
    private UUID bureauReportId;

    @Column(name = "hit_code", length = 20)
    private String hitCode;

    @Column(name = "success_code", length = 20)
    private String successCode;

    @Column(name = "report_order_no", length = 80)
    private String reportOrderNo;

    @Column(name = "score_name", length = 80)
    private String scoreName;

    @Column(name = "account_count")
    private Integer accountCount;

    @Column(name = "active_account_count")
    private Integer activeAccountCount;

    @Column(name = "writeoff_count")
    private Integer writeoffCount;

    @Column(name = "total_past_due", precision = 18, scale = 2)
    private BigDecimal totalPastDue;

    @Column(name = "most_severe_status_24m", length = 40)
    private String mostSevereStatus24m;

    @Column(name = "total_balance", precision = 18, scale = 2)
    private BigDecimal totalBalance;

    @Column(name = "total_sanction", precision = 18, scale = 2)
    private BigDecimal totalSanction;

    @Column(name = "total_credit_limit", precision = 18, scale = 2)
    private BigDecimal totalCreditLimit;

    @Column(name = "total_monthly_payment", precision = 18, scale = 2)
    private BigDecimal totalMonthlyPayment;

    @Column(name = "highest_sanction", precision = 18, scale = 2)
    private BigDecimal highestSanction;

    @Column(name = "highest_balance", precision = 18, scale = 2)
    private BigDecimal highestBalance;

    @Column(name = "average_open_balance", precision = 18, scale = 2)
    private BigDecimal averageOpenBalance;

    @Column(name = "age_of_oldest_trade_months")
    private Integer ageOfOldestTradeMonths;

    @Column(name = "open_trade_count")
    private Integer openTradeCount;

    @Column(name = "past_due_account_count")
    private Integer pastDueAccountCount;

    @Column(name = "zero_balance_account_count")
    private Integer zeroBalanceAccountCount;

    @Column(name = "highest_credit", precision = 18, scale = 2)
    private BigDecimal highestCredit;

    @Column(name = "total_high_credit", precision = 18, scale = 2)
    private BigDecimal totalHighCredit;

    @Column(name = "enquiry_total")
    private Integer enquiryTotal;

    @Column(name = "enquiry_past_30d")
    private Integer enquiryPast30d;

    @Column(name = "enquiry_past_12m")
    private Integer enquiryPast12m;

    @Column(name = "enquiry_past_24m")
    private Integer enquiryPast24m;

    @Column(name = "enquiry_recent_date")
    private LocalDate enquiryRecentDate;

    @Column(name = "recent_accounts_opened_90d")
    private Integer recentAccountsOpened90d;

    @Column(name = "recent_accounts_updated_90d")
    private Integer recentAccountsUpdated90d;

    @Column(name = "recent_accounts_delinquent_90d")
    private Integer recentAccountsDelinquent90d;

    @Column(name = "recent_inquiries_90d")
    private Integer recentInquiries90d;

    @Column(name = "report_time", length = 16)
    private String reportTime;

    @Column(name = "enquiry_summary_purpose", length = 40)
    private String enquirySummaryPurpose;

    @Column(name = "all_lines_ever_written", precision = 18, scale = 2)
    private BigDecimal allLinesEverWritten;

    @Column(name = "all_lines_ever_written_9m", precision = 18, scale = 2)
    private BigDecimal allLinesEverWritten9m;

    @Column(name = "all_lines_ever_written_6m", precision = 18, scale = 2)
    private BigDecimal allLinesEverWritten6m;

    @Column(name = "recent_account_narrative", length = 400)
    private String recentAccountNarrative;

    @Column(name = "oldest_account_narrative", length = 400)
    private String oldestAccountNarrative;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
