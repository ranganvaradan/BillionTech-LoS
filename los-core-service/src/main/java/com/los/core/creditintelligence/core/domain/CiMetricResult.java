package com.los.core.creditintelligence.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_metric_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiMetricResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "bureau_report_id")
    private UUID bureauReportId;

    @Column(name = "fact_snapshot_id")
    private UUID factSnapshotId;

    @Column(name = "evaluation_id")
    private UUID evaluationId;

    @Column(name = "metric_code", nullable = false, length = 120)
    private String metricCode;

    @Column(name = "metric_version", nullable = false, length = 40)
    private String metricVersion;

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb")
    private Map<String, Object> value;

    @Column(name = "data_quality_status", nullable = false, length = 40)
    @Builder.Default
    private String dataQualityStatus = "OK";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "included_references", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> includedReferences = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_references", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> excludedReferences = List.of();

    @Column(name = "unknown_count", nullable = false)
    @Builder.Default
    private int unknownCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_record_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> sourceRecordIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> evidence = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
