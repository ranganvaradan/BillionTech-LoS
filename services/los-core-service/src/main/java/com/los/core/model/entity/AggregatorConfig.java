package com.los.core.model.entity;

import com.los.core.model.enums.IntegrationCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DB-driven routing for external providers. Credentials remain in
 * application properties / a future {@code aggregator_credentials} table; this
 * model only governs <strong>which</strong> provider to try and in <strong>what order</strong>.
 */
@Entity
@Table(name = "aggregator_routing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregatorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 40)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false, length = 20)
    private IntegrationCategory integrationType;

    /**
     * When set, this row applies only to that KYC step; when null, applies to all KYC steps
     * (subject to {@code IKycProvider#supports}).
     */
    @Column(name = "kyc_step_type", length = 40)
    private String kycStepType;

    /**
     * Higher value = higher precedence when ordering candidates (same convention as
     * {@link com.los.core.service.integration.providers.IKycProvider#getPriority}).
     */
    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "allow_fallback", nullable = false)
    @Builder.Default
    private boolean allowFallback = true;

    /**
     * When {@link #integrationType} is {@code ESIGN}, optionally scopes this row to
     * {@link com.los.core.model.enums.KycStepType#ESIGN_KFS} or {@code ESIGN_AGREEMENT}.
     * {@code null} = applies to all eSign document flows.
     */
    @Column(name = "esign_step_type", length = 40)
    private String esignStepType;

    /** Human purpose / applies-to (seeded for admin matrix UI; not used by the router for decisions). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
