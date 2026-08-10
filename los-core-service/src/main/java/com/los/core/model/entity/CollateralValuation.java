package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * BR-4.9: Collateral valuation record.
 */
@Entity
@Table(name = "collateral_valuations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollateralValuation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false, length = 50)
    private String collateralType; // PROPERTY, VEHICLE, GOLD, FIXED_DEPOSIT, SHARES, MACHINERY

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String address;

    @Column(precision = 15, scale = 2)
    private BigDecimal marketValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal forcedSaleValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal valuationAmount; // Accepted value for LTV calculation

    @Column(length = 200)
    private String valuerId;

    @Column(length = 200)
    private String valuerName;

    private Instant valuationDate;

    private Instant valuationExpiry;

    @Column(length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED, EXPIRED

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details; // Additional valuation details

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
