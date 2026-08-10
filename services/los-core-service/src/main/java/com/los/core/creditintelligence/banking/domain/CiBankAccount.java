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
@Table(name = "ci_bank_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    @Column(name = "institution_code", length = 64)
    private String institutionCode;

    @Column(name = "account_number_hash", length = 128)
    private String accountNumberHash;

    @Column(name = "account_number_last4", length = 8)
    private String accountNumberLast4;

    @Column(name = "ifsc", length = 20)
    private String ifsc;

    @Column(name = "account_type", nullable = false, length = 40)
    @Builder.Default
    private String accountType = BankAccountType.UNKNOWN.name();

    @Column(name = "ownership_type", length = 40)
    private String ownershipType;

    @Column(name = "holder_name", length = 300)
    private String holderName;

    @Column(name = "holder_name_match_status", nullable = false, length = 40)
    @Builder.Default
    private String holderNameMatchStatus = OwnershipMatchStatus.UNKNOWN.name();

    @Column(name = "holder_name_match_score", precision = 8, scale = 4)
    private BigDecimal holderNameMatchScore;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "branch", length = 200)
    private String branch;

    @Column(name = "statement_from")
    private LocalDate statementFrom;

    @Column(name = "statement_to")
    private LocalDate statementTo;

    @Column(name = "opening_balance", precision = 18, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 18, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "sanctioned_limit", precision = 18, scale = 2)
    private BigDecimal sanctionedLimit;

    @Column(name = "drawing_power", precision = 18, scale = 2)
    private BigDecimal drawingPower;

    @Column(name = "overdraft_limit", precision = 18, scale = 2)
    private BigDecimal overdraftLimit;

    @Column(name = "account_status", nullable = false, length = 40)
    @Builder.Default
    private String accountStatus = "ACTIVE";

    @Column(name = "operating_relevance", nullable = false, length = 40)
    @Builder.Default
    private String operatingRelevance = "UNKNOWN";

    @Column(name = "aggregation_eligible", nullable = false)
    @Builder.Default
    private boolean aggregationEligible = true;

    @Column(name = "source_quality_status", nullable = false, length = 40)
    @Builder.Default
    private String sourceQualityStatus = "OK";

    @Column(name = "parser_version", nullable = false, length = 40)
    private String parserVersion;

    @Column(name = "normalizer_version", nullable = false, length = 40)
    private String normalizerVersion;

    @Column(name = "idempotency_key", length = 300)
    private String idempotencyKey;

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
