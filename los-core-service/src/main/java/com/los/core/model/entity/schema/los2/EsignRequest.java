package com.los.core.model.entity.schema.los2;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted eSign attempt / outcome (LOS_Design_v2 {@code esign_requests}).
 * Application-level eSign id may still be mirrored on {@code loan_applications.esign_transaction_id}.
 */
@Entity
@Table(name = "esign_requests", indexes = {
        @Index(name = "idx_esign_requests_app", columnList = "application_id"),
        @Index(name = "idx_esign_requests_provider_id", columnList = "provider, provider_request_id"),
        @Index(name = "idx_esign_requests_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsignRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "provider_request_id", length = 200)
    private String providerRequestId;

    @Column(name = "signing_url", columnDefinition = "text")
    private String signingUrl;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "signer_name", length = 200)
    private String signerName;

    @Column(name = "signer_aadhaar_last4", length = 4)
    private String signerAadhaarLast4;

    @Column(name = "signed_document_url", columnDefinition = "text")
    private String signedDocumentUrl;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response")
    private Map<String, Object> rawResponse;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
