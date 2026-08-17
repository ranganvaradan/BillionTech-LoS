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
@Table(name = "ci_gacat_derived_calculation_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiGacatDerivedCalculationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "canonical_parameter_id", nullable = false, length = 200)
    private String canonicalParameterId;

    @Column(name = "scope", nullable = false, length = 40)
    @Builder.Default
    private String scope = "PLATFORM";

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "DEFINED";

    /** AUTHORED_EXPRESSION (spine formula) or BUILT_IN_CODE (BureauMetricService). */
    @Column(name = "calculation_type", nullable = false, length = 40)
    @Builder.Default
    private String calculationType = "AUTHORED_EXPRESSION";

    @Column(name = "result_type", nullable = false, length = 40)
    @Builder.Default
    private String resultType = "NUMBER";

    @Column(name = "unit", length = 40)
    private String unit;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expression_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> expressionJson = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dependency_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> dependencyIds = new ArrayList<>();

    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Integer versionNo = 1;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
