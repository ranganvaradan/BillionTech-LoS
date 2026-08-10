package com.los.core.creditintelligence.policystudio.lifecycle.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_p2_validation_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiP2ValidationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "application_token", length = 120)
    private String applicationToken;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "origin_classification", nullable = false, length = 60)
    private String originClassification;

    @Column(name = "evaluation_business_date")
    private LocalDate evaluationBusinessDate;

    @Column(name = "resolver_outcome", length = 60)
    private String resolverOutcome;

    @Column(name = "applicability_id")
    private UUID applicabilityId;

    @Column(name = "policy_version_id")
    private UUID policyVersionId;

    @Column(name = "executable_package_id")
    private UUID executablePackageId;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "evaluation_context_id")
    private UUID evaluationContextId;

    @Column(name = "evaluation_content_hash", length = 128)
    private String evaluationContentHash;

    @Column(name = "shadow_recommendation_hash", length = 128)
    private String shadowRecommendationHash;

    @Column(name = "legacy_outcome", length = 80)
    private String legacyOutcome;

    @Column(name = "shadow_outcome", length = 80)
    private String shadowOutcome;

    @Column(name = "comparison_class", length = 60)
    private String comparisonClass;

    @Column(name = "replay_pass")
    private Boolean replayPass;

    @Column(name = "routing_success")
    private Boolean routingSuccess;

    @Column(name = "package_exact")
    private Boolean packageExact;

    @Column(name = "underwriting_hook_used", nullable = false)
    @Builder.Default
    private Boolean underwritingHookUsed = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> evidence = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
