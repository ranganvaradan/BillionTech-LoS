package com.los.core.creditintelligence.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_underwriting_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiUnderwritingFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "canonical_path", nullable = false, length = 200)
    private String canonicalPath;

    @Column(name = "subject_entity_id")
    private UUID subjectEntityId;

    @Column(name = "value_type", nullable = false, length = 40)
    private String valueType;

    /**
     * JSON envelope for the fact value. Always a map (typically {@code {"v": &lt;scalar&gt;}}
     * or a structured object). Typed as {@code Map} — not {@code Object} — because Hibernate 6.5
     * {@code AbstractJsonFormatMapper} treats {@code Object.class} JSON attributes as already-serialized
     * Strings and ClassCastExceptions on Map values (HHH-19964).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> value;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "as_of")
    private Instant asOf;

    @Column(name = "classification", nullable = false, length = 40)
    private String classification;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "quality_status", nullable = false, length = 40)
    @Builder.Default
    private String qualityStatus = "OK";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_record_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> sourceRecordIds = List.of();

    @Column(name = "derivation_reference", length = 200)
    private String derivationReference;

    @Column(name = "normalizer_version", nullable = false, length = 40)
    @Builder.Default
    private String normalizerVersion = "F1";

    @Column(name = "supersedes_fact_id")
    private UUID supersedesFactId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
}
