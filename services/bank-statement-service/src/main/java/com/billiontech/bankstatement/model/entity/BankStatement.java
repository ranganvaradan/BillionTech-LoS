package com.billiontech.bankstatement.model.entity;

import com.billiontech.bankstatement.model.enums.AccountType;
import com.billiontech.bankstatement.model.enums.ParsingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bank_statements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "account_holder_name")
    private String accountHolderName;

    @Column(name = "account_number_masked")
    private String accountNumberMasked;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "ifsc_code")
    private String ifscCode;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "account_type")
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(name = "statement_from_date")
    private LocalDate statementFromDate;

    @Column(name = "statement_to_date")
    private LocalDate statementToDate;

    @Column(name = "opening_balance")
    private BigDecimal openingBalance;

    @Column(name = "closing_balance")
    private BigDecimal closingBalance;

    @Column(name = "total_transactions")
    private Integer totalTransactions;

    @Column(name = "total_credit_amount")
    private BigDecimal totalCreditAmount;

    @Column(name = "total_debit_amount")
    private BigDecimal totalDebitAmount;

    @Column(name = "parsing_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ParsingStatus parsingStatus;

    @Column(name = "parsing_error")
    private String parsingError;

    @Column(name = "tamper_check_status")
    private String tamperCheckStatus;

    @Column(name = "tamper_check_details")
    private String tamperCheckDetails;

    @Column(name = "application_id")
    private String applicationId;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BankTransaction> transactions = new ArrayList<>();

    @OneToOne(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    private StatementAnalysis analysis;

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MonthlySummary> monthlySummaries = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (parsingStatus == null) {
            parsingStatus = ParsingStatus.UPLOADED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
