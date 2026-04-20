package com.los.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lms_repayment_callback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmsRepaymentCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_number", nullable = false)
    private String applicationNumber;

    @Column(name = "encore_account_id")
    private String encoreAccountId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "repayment_type")
    private String repaymentType;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "principal_component")
    private BigDecimal principalComponent;

    @Column(name = "interest_component")
    private BigDecimal interestComponent;

    @Column(name = "penalty_component")
    private BigDecimal penaltyComponent;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "utr_number")
    private String utrNumber;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
