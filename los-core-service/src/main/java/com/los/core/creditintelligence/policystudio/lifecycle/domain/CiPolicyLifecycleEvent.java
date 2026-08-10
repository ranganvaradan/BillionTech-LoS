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
@Table(name = "ci_policy_lifecycle_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "applicability_id", nullable = false)
    private UUID applicabilityId;

    @Column(name = "policy_document_id")
    private UUID policyDocumentId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "previous_status", length = 40)
    private String previousStatus;

    @Column(name = "new_status", length = 40)
    private String newStatus;

    @Column(name = "actor", length = 120)
    private String actor;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
