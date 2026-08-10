package com.los.core.creditintelligence.decisionpolicy.corpus.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ci_dp_v1_validation_defect")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiDpV1ValidationDefect {

    @Id
    private UUID id;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "application_token", length = 120)
    private String applicationToken;

    @Column(name = "severity", nullable = false, length = 40)
    private String severity;

    @Column(name = "component", length = 80)
    private String component;

    @Column(name = "defect_type", nullable = false, length = 80)
    private String defectType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "blocking", nullable = false)
    @Builder.Default
    private boolean blocking = false;

    @Column(name = "resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
