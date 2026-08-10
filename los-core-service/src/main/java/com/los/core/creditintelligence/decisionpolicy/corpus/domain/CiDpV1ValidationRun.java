package com.los.core.creditintelligence.decisionpolicy.corpus.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_dp_v1_validation_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiDpV1ValidationRun {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "certification_status", nullable = false, length = 60)
    @Builder.Default
    private String certificationStatus = "INSUFFICIENT_EVIDENCE";

    @Column(name = "real_stored_count", nullable = false)
    @Builder.Default
    private int realStoredCount = 0;

    @Column(name = "usable_count", nullable = false)
    @Builder.Default
    private int usableCount = 0;

    @Column(name = "replay_pass_rate", precision = 8, scale = 4)
    private BigDecimal replayPassRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> summary = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "export_spec_snapshot", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> exportSpecSnapshot = Map.of();

    @Column(name = "allow_canonical_authority", nullable = false)
    @Builder.Default
    private boolean allowCanonicalAuthority = false;

    @Column(name = "shadow_only", nullable = false)
    @Builder.Default
    private boolean shadowOnly = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;
}
