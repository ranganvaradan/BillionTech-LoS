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
@Table(name = "lms_account_summary")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmsAccountSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_number", nullable = false)
    private String applicationNumber;

    @Column(name = "encore_account_id")
    private String encoreAccountId;

    @Column(name = "loan_status")
    private String loanStatus;

    @Column(name = "sanctioned_amount")
    private BigDecimal sanctionedAmount;

    @Column(name = "disbursed_amount")
    private BigDecimal disbursedAmount;

    @Column(name = "outstanding_principal")
    private BigDecimal outstandingPrincipal;

    @Column(name = "total_paid")
    private BigDecimal totalPaid;

    @Column(name = "overdue_amount")
    private BigDecimal overdueAmount;

    @Column(name = "fee_bl_due")
    private BigDecimal feeBlDue;

    @Column(name = "fee_lender_due")
    private BigDecimal feeLenderDue;

    /** JSON array: vendor {@code accountStatementEntries} from findSummaries when present. */
    @Column(name = "encore_account_statement_entries_json", columnDefinition = "TEXT")
    private String encoreAccountStatementEntriesJson;

    @Column(name = "total_emis")
    private Integer totalEmis;

    @Column(name = "paid_emis")
    private Integer paidEmis;

    @Column(name = "overdue_emis")
    private Integer overdueEmis;

    @Column(name = "next_emi_date")
    private LocalDate nextEmiDate;

    @Column(name = "next_emi_amount")
    private BigDecimal nextEmiAmount;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "dpd")
    private Integer dpd;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
