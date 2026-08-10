package com.los.core.creditintelligence.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_metric_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiMetricDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "metric_code", nullable = false, length = 120)
    private String metricCode;

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "domain", nullable = false, length = 64)
    @Builder.Default
    private String domain = "BUREAU";

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> definitionJson = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
