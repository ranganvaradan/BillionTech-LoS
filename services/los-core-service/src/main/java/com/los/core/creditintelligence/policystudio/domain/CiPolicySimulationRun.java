package com.los.core.creditintelligence.policystudio.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_simulation_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicySimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "draft_package_id")
    private UUID draftPackageId;

    @Column(name = "simulation_label", nullable = false, length = 80)
    @Builder.Default
    private String simulationLabel = "VALIDATION_FIXTURE_SIMULATION";

    @Column(name = "dsl_version", nullable = false, length = 40)
    @Builder.Default
    private String dslVersion = "POLICY_DSL_V1";

    @Column(name = "evaluation_semantics", nullable = false, length = 80)
    @Builder.Default
    private String evaluationSemantics = "POLICY_DSL_EVALUATION_SEMANTICS_V1";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> summary = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "case_results", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> caseResults = List.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
