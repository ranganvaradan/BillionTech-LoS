package com.los.core.creditintelligence.aiunderwriter.domain;

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
@Table(name = "ci_ai_scenario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiAiScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "analysis_request_id")
    private UUID analysisRequestId;

    @Column(name = "suggestion_id")
    private UUID suggestionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> requestPayload = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deterministic_result", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> deterministicResult = new LinkedHashMap<>();

    @Column(name = "ai_explanation", columnDefinition = "text")
    private String aiExplanation;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "COMPUTED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
