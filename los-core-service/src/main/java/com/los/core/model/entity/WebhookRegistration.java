package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * BR-18.4: Webhook registration for partner event subscriptions.
 */
@Entity
@Table(name = "webhook_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String partnerName;

    @Column(nullable = false, length = 500)
    private String callbackUrl;

    @Column(nullable = false, length = 100)
    private String eventType; // APPLICATION_STATUS_CHANGE, DISBURSEMENT, KYC_COMPLETE, etc.

    @Column(length = 200)
    private String secretKey; // HMAC signing key for webhook payload

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private int failureCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> headers; // Custom headers to include

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
