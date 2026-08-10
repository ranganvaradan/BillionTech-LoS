package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "nach_mandates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NachMandate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, unique = true, length = 50)
    private String mandateReference;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "CREATED";

    @Column(nullable = false, length = 100)
    private String bankName;

    @Column(nullable = false, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 11)
    private String ifscCode;

    @Column(nullable = false, length = 200)
    private String accountHolderName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String frequency = "MONTHLY";

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(length = 50)
    private String umrn;

    private Instant registeredAt;

    private Instant cancelledAt;

    @Column(length = 200)
    private String cancelReason;

    @Column(length = 50)
    private String esignTransactionId;

    private boolean esigned;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
