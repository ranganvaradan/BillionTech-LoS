package com.los.core.creditintelligence.bureau.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_bureau_tradeline")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBureauTradeline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bureau_report_id", nullable = false)
    private UUID bureauReportId;

    @Column(name = "provider_tradeline_ref", length = 200)
    private String providerTradelineRef;

    @Column(name = "lender_name", length = 200)
    private String lenderName;

    @Column(name = "account_type_raw", length = 120)
    private String accountTypeRaw;

    @Column(name = "product_category", nullable = false, length = 64)
    private String productCategory;

    @Column(name = "ownership_type", length = 40)
    private String ownershipType;

    @Column(name = "secured")
    private Boolean secured;

    @Column(name = "revolving")
    private Boolean revolving;

    @Column(name = "opened_date")
    private LocalDate openedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "last_reported_date")
    private LocalDate lastReportedDate;

    @Column(name = "sanctioned_amount", precision = 18, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(name = "high_credit", precision = 18, scale = 2)
    private BigDecimal highCredit;

    @Column(name = "current_balance", precision = 18, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "overdue_amount", precision = 18, scale = 2)
    private BigDecimal overdueAmount;

    @Column(name = "emi_amount", precision = 18, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "interest_rate", precision = 10, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "tenure_months")
    private Integer tenureMonths;

    @Column(name = "asset_classification", length = 80)
    private String assetClassification;

    @Column(name = "suit_filed", nullable = false)
    @Builder.Default
    private boolean suitFiled = false;

    @Column(name = "wilful_default", nullable = false)
    @Builder.Default
    private boolean wilfulDefault = false;

    @Column(name = "written_off_amount", precision = 18, scale = 2)
    private BigDecimal writtenOffAmount;

    @Column(name = "settlement_amount", precision = 18, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "restructured", nullable = false)
    @Builder.Default
    private boolean restructured = false;

    @Column(name = "settled", nullable = false)
    @Builder.Default
    private boolean settled = false;

    @Column(name = "written_off", nullable = false)
    @Builder.Default
    private boolean writtenOff = false;

    @Column(name = "collateral_type", length = 80)
    private String collateralType;

    @Column(name = "collateral_value", precision = 18, scale = 2)
    private BigDecimal collateralValue;

    @Column(name = "account_status", length = 80)
    private String accountStatus;

    @Column(name = "data_quality_status", nullable = false, length = 40)
    @Builder.Default
    private String dataQualityStatus = "OK";

    @Column(name = "is_live")
    private Boolean isLive;

    @Column(name = "live_definition_version", length = 80)
    private String liveDefinitionVersion;

    @Column(name = "duplicate_of_tradeline_id")
    private UUID duplicateOfTradelineId;

    @Column(name = "duplicate_group_id")
    private UUID duplicateGroupId;

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
