package com.los.core.creditintelligence.aiunderwriter.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_ai_analysis_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiAiAnalysisRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "policy_evaluation_id")
    private UUID policyEvaluationId;

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "context_version", length = 40)
    private String contextVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_output_types", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> requestedOutputTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> contextPayload = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prompt_versions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> promptVersions = new LinkedHashMap<>();

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = AnalysisRequestStatus.PENDING.name();

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "model_provider", length = 80)
    private String modelProvider;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "model_version", length = 80)
    private String modelVersion;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
