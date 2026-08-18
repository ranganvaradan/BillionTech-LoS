package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Table(name = "ci_canonical_shadow_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanonicalShadowEvaluationEntity {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "underwriting_evaluation_id")
    private UUID underwritingEvaluationId;

    @Column(name = "freeze_row_id")
    private UUID freezeRowId;

    @Column(name = "identity_hash", nullable = false, length = 64)
    private String identityHash;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "canonical_decision", length = 40)
    private String canonicalDecision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> reasonCodes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> parameterEvidence = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> ruleEvidence = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scorecard_evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> scorecardEvidence = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_result", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> policyResult = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> aggregation = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
