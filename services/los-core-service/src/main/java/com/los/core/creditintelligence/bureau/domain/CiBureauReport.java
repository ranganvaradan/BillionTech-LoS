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
@Table(name = "ci_bureau_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBureauReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(name = "subject_entity_id")
    private UUID subjectEntityId;

    @Column(name = "subject_type", nullable = false, length = 40)
    private String subjectType;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "provider_report_ref", length = 200)
    private String providerReportRef;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "score")
    private Integer score;

    @Column(name = "score_type", length = 80)
    private String scoreType;

    @Column(name = "risk_grade", length = 40)
    private String riskGrade;

    @Column(name = "name_match_score", precision = 8, scale = 4)
    private BigDecimal nameMatchScore;

    @Column(name = "identifier_match_score", precision = 8, scale = 4)
    private BigDecimal identifierMatchScore;

    @Column(name = "address_match_score", precision = 8, scale = 4)
    private BigDecimal addressMatchScore;

    @Column(name = "quality_status", nullable = false, length = 40)
    @Builder.Default
    private String qualityStatus = "OK";

    @Column(name = "parser_version", nullable = false, length = 40)
    private String parserVersion;

    @Column(name = "normalizer_version", nullable = false, length = 40)
    private String normalizerVersion;

    @Column(name = "tradelines_present", nullable = false)
    @Builder.Default
    private boolean tradelinesPresent = false;

    @Column(name = "tradeline_extraction_status", nullable = false, length = 40)
    @Builder.Default
    private String tradelineExtractionStatus = "UNKNOWN";

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
