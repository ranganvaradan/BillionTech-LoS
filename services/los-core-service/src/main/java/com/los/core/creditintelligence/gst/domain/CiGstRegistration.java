package com.los.core.creditintelligence.gst.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_gst_registration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiGstRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "gstin", nullable = false, length = 20)
    private String gstin;

    @Column(name = "pan_hash", length = 128)
    private String panHash;

    @Column(name = "legal_name", length = 300)
    private String legalName;

    @Column(name = "trade_name", length = 300)
    private String tradeName;

    @Column(name = "registration_type", length = 80)
    private String registrationType;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "cancellation_date")
    private LocalDate cancellationDate;

    @Column(name = "registration_status", nullable = false, length = 40)
    @Builder.Default
    private String registrationStatus = "UNKNOWN";

    @Column(name = "state_code", length = 10)
    private String stateCode;

    @Column(name = "principal_place", length = 500)
    private String principalPlace;

    @Column(name = "business_constitution", length = 120)
    private String businessConstitution;

    @Column(name = "taxpayer_type", length = 80)
    private String taxpayerType;

    @Column(name = "filing_frequency", length = 40)
    private String filingFrequency;

    @Column(name = "quality_status", nullable = false, length = 40)
    @Builder.Default
    private String qualityStatus = "OK";

    @Column(name = "parser_version", nullable = false, length = 40)
    private String parserVersion;

    @Column(name = "normalizer_version", nullable = false, length = 40)
    private String normalizerVersion;

    @Column(name = "idempotency_key", length = 300)
    private String idempotencyKey;

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
