package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "api_name", nullable = false, length = 100)
    private String apiName;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "request_time")
    private Instant requestTime;

    @Column(name = "response_time")
    private Instant responseTime;
}
