package com.los.core.creditintelligence.tax.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_tax_return_revision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiTaxReturnRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "assessment_year", nullable = false, length = 9)
    private String assessmentYear;

    @Column(name = "original_return_id")
    private UUID originalReturnId;

    @Column(name = "effective_return_id")
    private UUID effectiveReturnId;

    @Column(name = "selection_basis", nullable = false, length = 120)
    private String selectionBasis;

    @Column(name = "selection_version", nullable = false, length = 40)
    @Builder.Default
    private String selectionVersion = TaxConstants.ITR_EFFECTIVE_RETURN_SELECTION_V1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_references", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> sourceReferences = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
