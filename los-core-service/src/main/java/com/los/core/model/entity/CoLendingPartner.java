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
 * BR-17: Co-Lending partner entity.
 */
@Entity
@Table(name = "co_lending_partners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoLendingPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String partnerName;

    @Column(nullable = false, unique = true, length = 20)
    private String partnerCode;

    @Column(length = 30)
    private String partnerType; // BANK, NBFC, HFC

    @Column(precision = 5, scale = 2)
    private BigDecimal defaultApportionmentPercent; // Partner's share %

    @Column(precision = 5, scale = 2)
    private BigDecimal maxExposureLimit;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRateSpread; // Rate spread over MCLR/repo

    @Column(length = 500)
    private String apiEndpoint;

    @Column(length = 200)
    private String contactEmail;

    @Column(length = 15)
    private String contactPhone;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config; // Flexible partner-specific config

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
