package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Scoped, auditable manual override of one canonical GACAT parameter on one application —
 * only permitted (see {@link ManualParameterOverridePolicy}) when the parameter's real source
 * is confirmed unavailable. Never a blanket bureau-score bypass.
 */
@Entity
@Table(name = "application_parameter_manual_override")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationParameterManualOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "canonical_parameter_id", nullable = false, length = 160)
    private String canonicalParameterId;

    @Column(name = "value_text", nullable = false, length = 500)
    private String valueText;

    @Column(name = "reason")
    private String reason;

    @Column(name = "entered_by", nullable = false, length = 120)
    private String enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
