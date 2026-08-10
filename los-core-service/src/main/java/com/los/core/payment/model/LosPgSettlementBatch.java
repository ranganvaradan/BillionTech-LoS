package com.los.core.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "los_pg_settlement_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LosPgSettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "settlement_utr", nullable = false, length = 100)
    private String settlementUtr;

    @Column(name = "settlement_mode", nullable = false, length = 30)
    @Builder.Default
    private String settlementMode = "PG";

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;
}
