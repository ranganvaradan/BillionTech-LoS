package com.billiontech.bankstatement.model.entity;

import com.billiontech.bankstatement.model.enums.TransactionCategory;
import com.billiontech.bankstatement.model.enums.TransactionChannel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id", nullable = false)
    private BankStatement statement;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "narration")
    private String narration;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "debit_amount")
    @Builder.Default
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount")
    @Builder.Default
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "running_balance")
    private BigDecimal runningBalance;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private TransactionCategory category;

    @Column(name = "sub_category")
    private String subCategory;

    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    private TransactionChannel channel;

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Column(name = "counterparty_account")
    private String counterpartyAccount;

    @Column(name = "is_bounce")
    @Builder.Default
    private Boolean isBounce = false;

    @Column(name = "is_reversal")
    @Builder.Default
    private Boolean isReversal = false;

    @Column(name = "is_circular")
    @Builder.Default
    private Boolean isCircular = false;

    @Column(name = "raw_description")
    private String rawDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
