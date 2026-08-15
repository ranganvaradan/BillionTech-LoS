package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "underwriting_scorecards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnderwritingScorecard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "borrower_type", nullable = false, length = 30)
    private String borrowerType;

    @Column(name = "loan_product", nullable = false, length = 80)
    private String loanProduct;

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geography", columnDefinition = "jsonb")
    private Map<String, Object> geography;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scorecard_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> scorecardJson = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "thresholds_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> thresholdsJson = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hard_rules_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> hardRulesJson = Map.of();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    /** DRAFT | IN_REVIEW | APPROVED | ACTIVE | RETIRED — SCORECARD-GOVERNANCE-1 */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "lineage_id")
    private UUID lineageId;

    @Column(name = "parent_scorecard_id")
    private UUID parentScorecardId;

    @Column(name = "activated_at")
    private Instant activatedAt;

    /**
     * Safety metadata: factorPolicies (missingData REQUIRED|OPTIONAL_SKIP|OPTIONAL_DEPRESS),
     * exclusiveBandMode, validation snapshot. Weight is never used for scoring.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> safetyJson = Map.of();

    /**
     * Maker/checker/activation evidence — SCORECARD-GOVERNANCE-1.
     * Not cloned into new versions (except empty requireMakerChecker shell).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "governance_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> governanceJson = Map.of();

    /** DP-3 — LEGACY_POINTS_V1 | POLICY_WEIGHTED_V2 */
    @Column(name = "scoring_mode", nullable = false, length = 40)
    @Builder.Default
    private String scoringMode = "LEGACY_POINTS_V1";

    /** DP-3 — optional reverse link to owning Policy Version. */
    @Column(name = "policy_document_id")
    private UUID policyDocumentId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Runtime-eligible ACTIVE only (not APPROVED/IN_REVIEW). */
    public boolean isExecutionActive() {
        return active || "ACTIVE".equalsIgnoreCase(status);
    }
}
