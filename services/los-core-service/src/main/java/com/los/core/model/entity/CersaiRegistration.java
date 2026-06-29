package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cersai_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CersaiRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    private UUID collateralValuationId;

    @Column(length = 50)
    private String cersaiId;

    @Column(nullable = false, length = 30)
    private String assetType;

    @Column(columnDefinition = "TEXT")
    private String assetDescription;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String registrationStatus = "PENDING";

    @Column(nullable = false, length = 30)
    private String securityInterestType;

    @Column(precision = 15, scale = 2)
    private BigDecimal securedAmount;

    @Column(length = 200)
    private String borrowerName;

    @Column(length = 10)
    private String borrowerPan;

    @Column(length = 200)
    private String lenderName;

    @Column(length = 21)
    private String lenderCin;

    private Instant registrationDate;

    private Instant expiryDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> responseData;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
