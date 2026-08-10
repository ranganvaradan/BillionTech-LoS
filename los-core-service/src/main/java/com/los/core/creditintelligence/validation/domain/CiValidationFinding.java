package com.los.core.creditintelligence.validation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_validation_finding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiValidationFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "validation_run_id", nullable = false)
    private UUID validationRunId;

    @Column(name = "finding_code", nullable = false, length = 120)
    private String findingCode;

    @Column(name = "severity", nullable = false, length = 40)
    @Builder.Default
    private String severity = "INFO";

    @Column(name = "category", nullable = false, length = 80)
    private String category;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> detail = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
