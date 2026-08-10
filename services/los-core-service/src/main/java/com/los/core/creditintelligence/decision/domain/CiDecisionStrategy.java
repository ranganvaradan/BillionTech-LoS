package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_decision_strategy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiDecisionStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lender_id")
    private UUID lenderId;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "strategy_code", nullable = false, length = 120)
    private String strategyCode;

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "SHADOW";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> content = Map.of();

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 120)
    private String publishedBy;
}
