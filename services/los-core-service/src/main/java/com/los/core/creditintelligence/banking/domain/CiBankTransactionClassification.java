package com.los.core.creditintelligence.banking.domain;

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
@Table(name = "ci_bank_transaction_classification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBankTransactionClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "subcategory", length = 64)
    private String subcategory;

    @Column(name = "cash_flow_class", length = 40)
    private String cashFlowClass;

    @Column(name = "method", nullable = false, length = 80)
    private String method;

    @Column(name = "classifier_version", nullable = false, length = 40)
    private String classifierVersion;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> evidence = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
