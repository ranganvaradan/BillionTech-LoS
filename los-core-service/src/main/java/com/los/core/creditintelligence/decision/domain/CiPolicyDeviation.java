package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_deviation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyDeviation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(name = "policy_rule_id", length = 120)
    private String policyRuleId;

    @Column(name = "original_outcome", length = 40)
    private String originalOutcome;

    @Column(name = "requested_override", length = 120)
    private String requestedOverride;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "severity", length = 40)
    private String severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compensating_factors", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> compensatingFactors = new ArrayList<>();

    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "authority_required", length = 80)
    private String authorityRequired;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = DeviationStatus.REQUESTED.name();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
