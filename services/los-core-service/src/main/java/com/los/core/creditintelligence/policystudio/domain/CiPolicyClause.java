package com.los.core.creditintelligence.policystudio.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_policy_clause")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiPolicyClause {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_document_id", nullable = false)
    private UUID policyDocumentId;

    @Column(name = "clause_number", length = 40)
    private String clauseNumber;

    @Column(name = "parent_clause_id")
    private UUID parentClauseId;

    @Column(name = "section", length = 200)
    private String section;

    @Column(name = "page")
    private Integer page;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "normalized_text", columnDefinition = "TEXT")
    private String normalizedText;

    @Column(name = "clause_type", nullable = false, length = 40)
    @Builder.Default
    private String clauseType = ClauseType.UNKNOWN.name();

    @Column(name = "product_scope", length = 120)
    private String productScope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effective_scope", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> effectiveScope = Map.of();

    @Column(name = "source_location", length = 200)
    private String sourceLocation;

    @Column(name = "extraction_confidence", precision = 8, scale = 4)
    private BigDecimal extractionConfidence;

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = "EXTRACTED";

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
