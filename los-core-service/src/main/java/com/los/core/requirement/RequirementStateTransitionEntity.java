package com.los.core.requirement;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement_state_transition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementStateTransitionEntity {

    public static final String FIELD_FULFILMENT = "FULFILMENT";
    public static final String FIELD_READINESS = "READINESS";
    public static final String FIELD_SOURCE = "SOURCE";

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "field_name", nullable = false, length = 32)
    private String fieldName;

    @Column(name = "from_state", length = 40)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 40)
    private String toState;

    @Column(length = 500)
    private String reason;

    @Column(length = 120)
    private String actor;

    @Column(name = "evidence_ref", length = 255)
    private String evidenceRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
