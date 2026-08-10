package com.los.core.creditintelligence.aiunderwriter.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_ai_underwriting_suggestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiAiUnderwritingSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "analysis_request_id")
    private UUID analysisRequestId;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "policy_evaluation_id")
    private UUID policyEvaluationId;

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Column(name = "title", length = 400)
    private String title;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> structuredPayload = new LinkedHashMap<>();

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_confidence", precision = 8, scale = 4)
    private BigDecimal modelConfidence;

    @Column(name = "grounding_coverage", precision = 8, scale = 4)
    private BigDecimal groundingCoverage;

    @Column(name = "evidence_completeness", precision = 8, scale = 4)
    private BigDecimal evidenceCompleteness;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limitations", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> limitations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> sourceRefs = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> modelMetadata = new LinkedHashMap<>();

    @Column(name = "prompt_version", length = 80)
    private String promptVersion;

    @Column(name = "output_marker", nullable = false, length = 40)
    @Builder.Default
    private String outputMarker = "AI_SUGGESTION";

    @Column(name = "authoritative", nullable = false)
    @Builder.Default
    private Boolean authoritative = false;

    @Column(name = "human_review_required", nullable = false)
    @Builder.Default
    private Boolean humanReviewRequired = true;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = SuggestionStatus.GENERATED.name();

    @Column(name = "grounding_status", length = 40)
    private String groundingStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
