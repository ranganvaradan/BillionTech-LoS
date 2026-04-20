package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * BR-17.2: Co-lending apportionment allocation per loan.
 */
@Entity
@Table(name = "co_lending_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoLendingAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = true)
    private UUID partnerId;

    @Column(nullable = false, length = 100)
    private String partnerName;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal sharePercent;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal shareAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal partnerInterestRate;

    @Column(length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, ACCEPTED, DISBURSED, SETTLED

    private String partnerReferenceId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
