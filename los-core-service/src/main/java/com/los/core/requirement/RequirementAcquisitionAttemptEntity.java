package com.los.core.requirement;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "requirement_acquisition_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementAcquisitionAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "plan_version", nullable = false)
    @Builder.Default
    private int planVersion = 1;

    @Column(name = "canonical_parameter_id", length = 120)
    private String canonicalParameterId;

    @Column(name = "source_key", nullable = false, length = 80)
    private String sourceKey;

    @Column(name = "fulfilment_mode", nullable = false, length = 40)
    private String fulfilmentMode;

    @Column(name = "execution_key", nullable = false, length = 200)
    private String executionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SourceAcquisitionState status;

    @Column(name = "provider_ref", length = 120)
    private String providerRef;

    @Column(name = "external_ref", length = 255)
    private String externalRef;

    @Column(name = "failure_class", length = 40)
    private String failureClass;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "previous_source_key", length = 80)
    private String previousSourceKey;

    @Column(name = "fallback_source_key", length = 80)
    private String fallbackSourceKey;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private int attemptNumber = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> resultSummary = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> provenance = new LinkedHashMap<>();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (resultSummary == null) {
            resultSummary = new LinkedHashMap<>();
        }
        if (provenance == null) {
            provenance = new LinkedHashMap<>();
        }
        if (status == null) {
            status = SourceAcquisitionState.QUEUED;
        }
    }

    public static String buildExecutionKey(UUID applicationId, int planVersion, String itemKey, String sourceKey) {
        return applicationId + "|" + planVersion + "|" + itemKey + "|" + sourceKey;
    }
}
