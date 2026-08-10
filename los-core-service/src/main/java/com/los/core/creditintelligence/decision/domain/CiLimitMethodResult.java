package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_limit_method_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiLimitMethodResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "method_code", nullable = false, length = 80)
    private String methodCode;

    @Column(name = "eligible_amount", precision = 18, scale = 2)
    private BigDecimal eligibleAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inputs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> inputs = new LinkedHashMap<>();

    @Column(name = "formula_version", length = 80)
    private String formulaVersion;

    @Column(name = "data_status", length = 40)
    private String dataStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> detail = new LinkedHashMap<>();
}
