package com.los.core.creditintelligence.banking.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_bank_duplicate_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBankDuplicateGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "canonical_transaction_id")
    private UUID canonicalTransactionId;

    @Column(name = "matching_basis", nullable = false, length = 200)
    private String matchingBasis;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "member_transaction_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> memberTransactionIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
