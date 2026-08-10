package com.los.core.creditintelligence.policystudio.lifecycle.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_shadow_routing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyShadowRouting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "evaluation_business_date", nullable = false)
    private LocalDate evaluationBusinessDate;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "requested_amount", precision = 18, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "borrower_type", length = 80)
    private String borrowerType;

    @Column(name = "intake_segment", length = 80)
    private String intakeSegment;

    @Column(name = "resolver_outcome", nullable = false, length = 60)
    private String resolverOutcome;

    @Column(name = "selected_applicability_id")
    private UUID selectedApplicabilityId;

    @Column(name = "selected_policy_version_id")
    private UUID selectedPolicyVersionId;

    @Column(name = "policy_name", length = 300)
    private String policyName;

    @Column(name = "policy_version_label", length = 40)
    private String policyVersionLabel;

    @Column(name = "applicability_reason", columnDefinition = "TEXT")
    private String applicabilityReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> evidence = new LinkedHashMap<>();

    @Column(name = "shadow_only", nullable = false)
    @Builder.Default
    private Boolean shadowOnly = true;

    @Column(name = "shadow_evaluation_status", length = 40)
    private String shadowEvaluationStatus;

    @Column(name = "shadow_evaluation_error", columnDefinition = "TEXT")
    private String shadowEvaluationError;

    @Column(name = "routing_latency_ms")
    private Long routingLatencyMs;

    @Column(name = "executable_package_id")
    private UUID executablePackageId;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "evaluation_content_hash", length = 128)
    private String evaluationContentHash;

    @Column(name = "shadow_recommendation_hash", length = 128)
    private String shadowRecommendationHash;

    @Column(name = "linkage_outcome", length = 60)
    private String linkageOutcome;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
