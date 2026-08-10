package com.los.core.creditintelligence.decisionpolicy.corpus.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_dp_v1_corpus_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiDpV1CorpusApplication {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_token", nullable = false, length = 120)
    private String applicationToken;

    @Column(name = "origin_classification", nullable = false, length = 60)
    private String originClassification;

    @Column(name = "product_code", length = 80)
    private String productCode;

    @Column(name = "borrower_type", length = 40)
    private String borrowerType;

    @Column(name = "evaluation_business_date")
    private LocalDate evaluationBusinessDate;

    @Column(name = "requested_amount", precision = 18, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "requested_tenure_months")
    private Integer requestedTenureMonths;

    @Column(name = "anonymisation_version", nullable = false, length = 40)
    @Builder.Default
    private String anonymisationVersion = "DP_V1_ANON_V1";

    @Column(name = "schema_version", nullable = false, length = 40)
    @Builder.Default
    private String schemaVersion = "DP_V1_CORPUS_SCHEMA_V1";

    @Column(name = "usable", nullable = false)
    @Builder.Default
    private boolean usable = true;

    @Column(name = "counts_toward_certification", nullable = false)
    @Builder.Default
    private boolean countsTowardCertification = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> payload = Map.of();

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 80)
    private String createdBy;
}
