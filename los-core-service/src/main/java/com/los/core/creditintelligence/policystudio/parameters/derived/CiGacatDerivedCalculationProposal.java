package com.los.core.creditintelligence.policystudio.parameters.derived;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_gacat_derived_calculation_proposal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiGacatDerivedCalculationProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "target_parameter_id", nullable = false, length = 200)
    private String targetParameterId;

    @Column(name = "target_parameter_name", length = 300)
    private String targetParameterName;

    @Column(name = "scope", nullable = false, length = 40)
    @Builder.Default
    private String scope = "PLATFORM";

    @Column(name = "proposal_status", nullable = false, length = 40)
    @Builder.Default
    private String proposalStatus = "DRAFT";

    @Column(name = "confidence", length = 20)
    private String confidence;

    @Column(name = "human_explanation")
    private String humanExplanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_expression", columnDefinition = "jsonb")
    private Map<String, Object> proposedExpression;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_dependencies", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> candidateDependencies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assumptions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> assumptions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limitations", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> limitations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_dependencies", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> missingDependencies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alternatives", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Map<String, Object>> alternatives = new ArrayList<>();

    @Column(name = "option_index", nullable = false)
    @Builder.Default
    private Integer optionIndex = 1;

    @Column(name = "recommended", nullable = false)
    @Builder.Default
    private Boolean recommended = false;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "rejected_by", length = 120)
    private String rejectedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "resulting_definition_id")
    private UUID resultingDefinitionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
