package com.los.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lms_loan_handover")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmsLoanHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_number", nullable = false, unique = true)
    private String applicationNumber;

    @Column(name = "borrower_name")
    private String borrowerName;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "sanctioned_amount", nullable = false)
    private BigDecimal sanctionedAmount;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "encore_account_id")
    private String encoreAccountId;

    @Column(name = "encore_transaction_id")
    private String encoreTransactionId;

    @Column(name = "lms_reference_id")
    private String lmsReferenceId;

    @Column(name = "handover_status", nullable = false)
    private String handoverStatus;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "first_emi_date")
    private LocalDate firstEmiDate;

    @Column(name = "emi_amount")
    private BigDecimal emiAmount;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
