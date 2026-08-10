package com.los.core.creditintelligence.bureau.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_bureau_duplicate_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiBureauDuplicateGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bureau_report_id", nullable = false)
    private UUID bureauReportId;

    @Column(name = "selected_tradeline_id")
    private UUID selectedTradelineId;

    @Column(name = "matching_basis", nullable = false, length = 200)
    private String matchingBasis;

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @Column(name = "human_review_required", nullable = false)
    @Builder.Default
    private boolean humanReviewRequired = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "member_tradeline_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> memberTradelineIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
