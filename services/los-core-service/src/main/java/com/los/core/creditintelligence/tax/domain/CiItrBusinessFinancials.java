package com.los.core.creditintelligence.tax.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_itr_business_financials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiItrBusinessFinancials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "itr_return_id", nullable = false)
    private UUID itrReturnId;

    @Column(name = "gross_receipts", precision = 18, scale = 2)
    private BigDecimal grossReceipts;

    @Column(name = "sales_turnover", precision = 18, scale = 2)
    private BigDecimal salesTurnover;

    @Column(name = "gross_profit", precision = 18, scale = 2)
    private BigDecimal grossProfit;

    @Column(name = "ebitda", precision = 18, scale = 2)
    private BigDecimal ebitda;

    @Column(name = "depreciation", precision = 18, scale = 2)
    private BigDecimal depreciation;

    @Column(name = "finance_cost", precision = 18, scale = 2)
    private BigDecimal financeCost;

    @Column(name = "profit_before_tax", precision = 18, scale = 2)
    private BigDecimal profitBeforeTax;

    @Column(name = "profit_after_tax", precision = 18, scale = 2)
    private BigDecimal profitAfterTax;

    @Column(name = "inventory", precision = 18, scale = 2)
    private BigDecimal inventory;

    @Column(name = "trade_receivables", precision = 18, scale = 2)
    private BigDecimal tradeReceivables;

    @Column(name = "cash_and_bank", precision = 18, scale = 2)
    private BigDecimal cashAndBank;

    @Column(name = "fixed_assets", precision = 18, scale = 2)
    private BigDecimal fixedAssets;

    @Column(name = "total_assets", precision = 18, scale = 2)
    private BigDecimal totalAssets;

    @Column(name = "trade_payables", precision = 18, scale = 2)
    private BigDecimal tradePayables;

    @Column(name = "short_term_borrowings", precision = 18, scale = 2)
    private BigDecimal shortTermBorrowings;

    @Column(name = "long_term_borrowings", precision = 18, scale = 2)
    private BigDecimal longTermBorrowings;

    @Column(name = "total_borrowings", precision = 18, scale = 2)
    private BigDecimal totalBorrowings;

    @Column(name = "total_liabilities", precision = 18, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(name = "capital", precision = 18, scale = 2)
    private BigDecimal capital;

    @Column(name = "net_worth", precision = 18, scale = 2)
    private BigDecimal netWorth;

    @Column(name = "extraction_quality", nullable = false, length = 40)
    @Builder.Default
    private String extractionQuality = "OK";

    @Column(name = "source_reference", length = 300)
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
