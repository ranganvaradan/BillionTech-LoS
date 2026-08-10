package com.los.core.creditintelligence.policystudio.lifecycle.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_p2_validation_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiP2ValidationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "applicability_id")
    private UUID applicabilityId;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "certification_status", nullable = false, length = 40)
    @Builder.Default
    private String certificationStatus = "INSUFFICIENT_EVIDENCE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Column(name = "allow_canonical_authority", nullable = false)
    @Builder.Default
    private Boolean allowCanonicalAuthority = false;

    @Column(name = "shadow_only", nullable = false)
    @Builder.Default
    private Boolean shadowOnly = true;

    @Column(name = "created_by", length = 120)
    private String createdBy;
}
