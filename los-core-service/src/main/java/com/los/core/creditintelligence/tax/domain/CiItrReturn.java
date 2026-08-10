package com.los.core.creditintelligence.tax.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_itr_return")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiItrReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "subject_entity_id")
    private UUID subjectEntityId;

    @Column(name = "subject_scope", nullable = false, length = 40)
    @Builder.Default
    private String subjectScope = SubjectScope.BORROWER_ENTITY.name();

    @Column(name = "source_record_id", nullable = false)
    private UUID sourceRecordId;

    @Column(name = "pan_hash", length = 128)
    private String panHash;

    @Column(name = "pan_last4", length = 8)
    private String panLast4;

    @Column(name = "assessment_year", nullable = false, length = 9)
    private String assessmentYear;

    @Column(name = "financial_year", length = 9)
    private String financialYear;

    @Column(name = "itr_form", nullable = false, length = 20)
    @Builder.Default
    private String itrForm = ItrForm.UNKNOWN.name();

    @Column(name = "filing_date")
    private LocalDate filingDate;

    @Column(name = "filing_section", length = 80)
    private String filingSection;

    @Column(name = "ack_reference_masked", length = 80)
    private String ackReferenceMasked;

    @Column(name = "filing_status", nullable = false, length = 40)
    @Builder.Default
    private String filingStatus = "UNKNOWN";

    @Column(name = "return_version_type", nullable = false, length = 40)
    @Builder.Default
    private String returnVersionType = ReturnVersionType.ORIGINAL.name();

    @Column(name = "is_effective", nullable = false)
    @Builder.Default
    private boolean effective = true;

    @Column(name = "revised_of_return_id")
    private UUID revisedOfReturnId;

    @Column(name = "tax_regime", length = 40)
    private String taxRegime;

    @Column(name = "residential_status", length = 40)
    private String residentialStatus;

    @Column(name = "audit_applicable")
    private Boolean auditApplicable;

    @Column(name = "quality_status", nullable = false, length = 40)
    @Builder.Default
    private String qualityStatus = "OK";

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
