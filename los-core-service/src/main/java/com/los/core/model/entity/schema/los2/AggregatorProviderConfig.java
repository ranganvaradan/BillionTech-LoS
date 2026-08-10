package com.los.core.model.entity.schema.los2;

import com.los.core.model.entity.AggregatorConfig;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Connection / credential metadata per external provider (LOS_Design_v2 {@code aggregator_configs}).
 * This is <strong>not</strong> {@link AggregatorConfig}, which maps to
 * {@code aggregator_routing} (provider priority and fallback for the integration router).
 */
@Entity
@Table(name = "aggregator_configs", indexes = {
        @Index(name = "idx_aggregator_configs_provider", columnList = "provider_name, is_active"),
        @Index(name = "idx_aggregator_configs_step", columnList = "step_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregatorProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    /**
     * Optional scope, e.g. KYC sub-step, BUREAU, ESIGN, or null for provider-wide settings.
     */
    @Column(name = "step_type", length = 50)
    private String stepType;

    @Column(name = "base_url", length = 2000)
    private String baseUrl;

    @Column(name = "auth_type", length = 40)
    private String authType;

    @Column(name = "client_id_enc", columnDefinition = "text")
    private String clientIdEnc;

    @Column(name = "client_secret_enc", columnDefinition = "text")
    private String clientSecretEnc;

    @Column(name = "token_url", length = 2000)
    private String tokenUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_config")
    private Map<String, Object> extraConfig;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "webhook_secret", columnDefinition = "text")
    private String webhookSecret;

    @Column(name = "environment", length = 40)
    private String environment;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
