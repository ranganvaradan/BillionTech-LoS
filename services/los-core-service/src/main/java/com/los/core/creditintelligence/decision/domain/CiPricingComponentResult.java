package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ci_pricing_component_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPricingComponentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "component_code", nullable = false, length = 80)
    private String componentCode;

    @Column(name = "value_bps", precision = 12, scale = 4)
    private BigDecimal valueBps;

    @Column(name = "basis", columnDefinition = "text")
    private String basis;

    @Column(name = "rule_version", length = 80)
    private String ruleVersion;

    @Column(name = "reason_code", length = 120)
    private String reasonCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = new ArrayList<>();
}
