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
@Table(name = "ci_form26as_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiForm26AsSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "subject_entity_id")
    private UUID subjectEntityId;

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "pan_hash", length = 128)
    private String panHash;

    @Column(name = "total_tds", precision = 18, scale = 2)
    private BigDecimal totalTds;

    @Column(name = "total_tcs", precision = 18, scale = 2)
    private BigDecimal totalTcs;

    @Column(name = "advance_tax", precision = 18, scale = 2)
    private BigDecimal advanceTax;

    @Column(name = "self_assessment_tax", precision = 18, scale = 2)
    private BigDecimal selfAssessmentTax;

    @Column(name = "refund", precision = 18, scale = 2)
    private BigDecimal refund;

    @Column(name = "other_credits", precision = 18, scale = 2)
    private BigDecimal otherCredits;

    @Column(name = "quality_status", nullable = false, length = 40)
    @Builder.Default
    private String qualityStatus = "OK";

    @Column(name = "parser_version", nullable = false, length = 40)
    private String parserVersion;

    @Column(name = "normalizer_version", nullable = false, length = 40)
    private String normalizerVersion;

    @Column(name = "idempotency_key", length = 300)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
