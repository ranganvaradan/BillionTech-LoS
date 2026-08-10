package com.los.core.creditintelligence.evaluation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_provider_observation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiProviderObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "source_record_id")
    private UUID sourceRecordId;

    @Column(name = "provider", nullable = false, length = 80)
    private String provider;

    @Column(name = "observation_code", nullable = false, length = 120)
    private String observationCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb")
    private Map<String, Object> value;

    @Column(name = "value_type", length = 40)
    private String valueType;

    @Column(name = "provider_analysis_version", length = 80)
    private String providerAnalysisVersion;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = List.of();

    @Column(name = "authoritative", nullable = false)
    @Builder.Default
    private boolean authoritative = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
