package com.los.core.creditintelligence.banking.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_bank_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(name = "provider_transaction_id", length = 200)
    private String providerTransactionId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "posting_date")
    private LocalDate postingDate;

    @Column(name = "description_raw", columnDefinition = "TEXT")
    private String descriptionRaw;

    @Column(name = "description_normalized", columnDefinition = "TEXT")
    private String descriptionNormalized;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "mode", nullable = false, length = 40)
    @Builder.Default
    private String mode = TxnMode.UNKNOWN.name();

    @Column(name = "utr_reference", length = 120)
    private String utrReference;

    @Column(name = "cheque_number", length = 40)
    private String chequeNumber;

    @Column(name = "counterparty_raw", length = 300)
    private String counterpartyRaw;

    @Column(name = "counterparty_normalized", length = 300)
    private String counterpartyNormalized;

    @Column(name = "counterparty_account_hash", length = 128)
    private String counterpartyAccountHash;

    @Column(name = "merchant", length = 200)
    private String merchant;

    @Column(name = "category", nullable = false, length = 64)
    @Builder.Default
    private String category = TxnCategory.UNKNOWN.name();

    @Column(name = "subcategory", length = 64)
    private String subcategory;

    @Column(name = "cash_flow_class", nullable = false, length = 40)
    @Builder.Default
    private String cashFlowClass = CashFlowClass.UNKNOWN.name();

    @Column(name = "purpose_class", length = 64)
    private String purposeClass;

    @Column(name = "recurring_flag", nullable = false)
    @Builder.Default
    private boolean recurringFlag = false;

    @Column(name = "emi_flag", nullable = false)
    @Builder.Default
    private boolean emiFlag = false;

    @Column(name = "bounce_flag", nullable = false)
    @Builder.Default
    private boolean bounceFlag = false;

    @Column(name = "return_flag", nullable = false)
    @Builder.Default
    private boolean returnFlag = false;

    @Column(name = "cash_flag", nullable = false)
    @Builder.Default
    private boolean cashFlag = false;

    @Column(name = "related_party_flag", nullable = false)
    @Builder.Default
    private boolean relatedPartyFlag = false;

    @Column(name = "self_transfer_flag", nullable = false)
    @Builder.Default
    private boolean selfTransferFlag = false;

    @Column(name = "lender_flag", nullable = false)
    @Builder.Default
    private boolean lenderFlag = false;

    @Column(name = "tax_payment_flag", nullable = false)
    @Builder.Default
    private boolean taxPaymentFlag = false;

    @Column(name = "salary_flag", nullable = false)
    @Builder.Default
    private boolean salaryFlag = false;

    @Column(name = "business_receipt_flag", nullable = false)
    @Builder.Default
    private boolean businessReceiptFlag = false;

    @Column(name = "business_payment_flag", nullable = false)
    @Builder.Default
    private boolean businessPaymentFlag = false;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "classification_method", length = 80)
    private String classificationMethod;

    @Column(name = "classifier_version", length = 40)
    private String classifierVersion;

    @Column(name = "duplicate_status", nullable = false, length = 40)
    @Builder.Default
    private String duplicateStatus = "UNIQUE";

    @Column(name = "duplicate_of_transaction_id")
    private UUID duplicateOfTransactionId;

    @Column(name = "duplicate_group_id")
    private UUID duplicateGroupId;

    @Column(name = "evidence_group_id")
    private UUID evidenceGroupId;

    @Column(name = "source_reference", length = 300)
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
