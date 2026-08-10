package com.los.core.creditintelligence.gst.domain;

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
@Table(name = "ci_gst_return_revision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiGstReturnRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "gst_registration_id", nullable = false)
    private UUID gstRegistrationId;

    @Column(name = "return_type", nullable = false, length = 40)
    private String returnType;

    @Column(name = "period_yyyy_mm", nullable = false, length = 7)
    private String periodYyyyMm;

    @Column(name = "original_period_id")
    private UUID originalPeriodId;

    @Column(name = "effective_period_id")
    private UUID effectivePeriodId;

    @Column(name = "selection_basis", nullable = false, length = 120)
    private String selectionBasis;

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
