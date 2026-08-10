package com.los.core.creditintelligence.policystudio.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_parameter",
        uniqueConstraints = @UniqueConstraint(name = "uq_ci_policy_param",
                columnNames = {"tenant_id", "code", "product_scope", "version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_scope", length = 120)
    private String productScope;

    @Column(name = "code", nullable = false, length = 120)
    private String code;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "value_type", nullable = false, length = 40)
    @Builder.Default
    private String valueType = "DECIMAL";

    @Column(name = "unit", nullable = false, length = 40)
    @Builder.Default
    private String unit = "INR";

    @Column(name = "source", nullable = false, length = 80)
    @Builder.Default
    private String source = "POLICY";

    @Column(name = "required", nullable = false)
    @Builder.Default
    private Boolean required = true;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
