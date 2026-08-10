package com.los.core.creditintelligence.validation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_cutover_readiness")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyCutoverReadiness {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "scope", nullable = false, length = 120)
    @Builder.Default
    private String scope = "PLATFORM";

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @Column(name = "replay_purity", nullable = false)
    @Builder.Default
    private boolean replayPurity = false;

    @Column(name = "critical_coverage_pct", precision = 8, scale = 4)
    private BigDecimal criticalCoveragePct;

    @Column(name = "binding_coverage_pct", precision = 8, scale = 4)
    private BigDecimal bindingCoveragePct;

    @Column(name = "silent_default_deps", nullable = false)
    @Builder.Default
    private int silentDefaultDeps = 0;

    @Column(name = "tenant_isolation_ok", nullable = false)
    @Builder.Default
    private boolean tenantIsolationOk = false;

    @Column(name = "provider_fixtures_ok", nullable = false)
    @Builder.Default
    private boolean providerFixturesOk = false;

    @Column(name = "multi_source_ok", nullable = false)
    @Builder.Default
    private boolean multiSourceOk = false;

    @Column(name = "security_critical_open", nullable = false)
    @Builder.Default
    private int securityCriticalOpen = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blockers", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> blockers = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimensions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> dimensions = Map.of();

    @Column(name = "assessed_at", nullable = false)
    @Builder.Default
    private Instant assessedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
