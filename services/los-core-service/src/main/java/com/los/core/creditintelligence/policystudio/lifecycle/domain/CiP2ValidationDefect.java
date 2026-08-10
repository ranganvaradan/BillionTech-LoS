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
@Table(name = "ci_p2_validation_defect")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiP2ValidationDefect {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "application_token", length = 120)
    private String applicationToken;

    @Column(name = "severity", nullable = false, length = 40)
    private String severity;

    @Column(name = "component", nullable = false, length = 80)
    private String component;

    @Column(name = "defect_type", nullable = false, length = 80)
    private String defectType;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "blocking", nullable = false)
    @Builder.Default
    private Boolean blocking = false;

    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> evidence = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
