package com.los.core.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "los_loan_payment_in_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LosLoanPaymentInProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pg_transaction_id", nullable = false)
    private UUID pgTransactionId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "application_number", length = 50)
    private String applicationNumber;

    @Column(name = "loan_product", length = 80)
    private String loanProduct;

    @Column(name = "borrower_user_id", nullable = false)
    private UUID borrowerUserId;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "pip_status", nullable = false, length = 20)
    @Builder.Default
    private String pipStatus = "OPEN";

    @Column(name = "settlement_batch_id")
    private UUID settlementBatchId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
