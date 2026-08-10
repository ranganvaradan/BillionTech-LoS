package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_kyc_policy_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiKycPolicyEvaluation {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "evaluation_business_date")
    private LocalDate evaluationBusinessDate;

    @Column(name = "policy_version_id")
    private UUID policyVersionId;

    @Column(name = "executable_package_id")
    private UUID executablePackageId;

    @Column(name = "policy_code", length = 120)
    private String policyCode;

    @Column(name = "policy_version", length = 40)
    private String policyVersion;

    @Column(name = "policy_content_hash", length = 128)
    private String policyContentHash;

    @Column(name = "routing_outcome", length = 80)
    private String routingOutcome;

    @Column(name = "overall_outcome", nullable = false, length = 40)
    private String overallOutcome;

    @Column(name = "production_kyc_outcome", length = 40)
    private String productionKycOutcome;

    @Column(name = "comparison_class", length = 80)
    private String comparisonClass;

    @Column(name = "review_required", nullable = false)
    @Builder.Default
    private boolean reviewRequired = false;

    @Column(name = "review_reason", length = 200)
    private String reviewReason;

    @Column(name = "deterministic_hash", nullable = false, length = 128)
    private String deterministicHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frozen_facts", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> frozenFacts = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "application_inputs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> applicationInputs = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_results", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> ruleResults = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comparison", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> comparison = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refer_payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> referPayload = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_provenance", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> workflowProvenance = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_facts", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> missingFacts = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fail_reasons", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> failReasons = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refer_reasons", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> referReasons = List.of();

    @Column(name = "authoritative", nullable = false)
    @Builder.Default
    private boolean authoritative = false;

    @Column(name = "shadow", nullable = false)
    @Builder.Default
    private boolean shadow = true;

    @Column(name = "certification_status", nullable = false, length = 40)
    @Builder.Default
    private String certificationStatus = "SHADOW_EVALUATION_READY";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
