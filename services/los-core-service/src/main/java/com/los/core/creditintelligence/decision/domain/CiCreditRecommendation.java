package com.los.core.creditintelligence.decision.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_credit_recommendation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiCreditRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "decision_input_id")
    private UUID decisionInputId;

    @Column(name = "decision_strategy_id")
    private UUID decisionStrategyId;

    @Column(name = "recommendation_version", length = 40)
    private String recommendationVersion;

    @Column(name = "recommendation_outcome", length = 40)
    private String recommendationOutcome;

    @Column(name = "recommended_facility_type", length = 80)
    private String recommendedFacilityType;

    @Column(name = "recommended_amount", precision = 18, scale = 2)
    private BigDecimal recommendedAmount;

    @Column(name = "recommended_tenure_months")
    private Integer recommendedTenureMonths;

    @Column(name = "recommended_repayment_frequency", length = 40)
    private String recommendedRepaymentFrequency;

    @Column(name = "recommended_emi", precision = 18, scale = 2)
    private BigDecimal recommendedEmi;

    @Column(name = "recommended_base_rate", precision = 12, scale = 6)
    private BigDecimal recommendedBaseRate;

    @Column(name = "recommended_risk_premium", precision = 12, scale = 6)
    private BigDecimal recommendedRiskPremium;

    @Column(name = "recommended_final_rate", precision = 12, scale = 6)
    private BigDecimal recommendedFinalRate;

    @Column(name = "recommended_processing_fee", precision = 18, scale = 2)
    private BigDecimal recommendedProcessingFee;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_collateral", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> recommendedCollateral = new LinkedHashMap<>();

    @Column(name = "recommended_ltv", precision = 12, scale = 6)
    private BigDecimal recommendedLtv;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_guarantors", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> recommendedGuarantors = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_precedent", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> conditionsPrecedent = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_subsequent", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> conditionsSubsequent = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "covenants", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> covenants = new ArrayList<>();

    @Column(name = "approval_authority_level", length = 80)
    private String approvalAuthorityLevel;

    @Column(name = "human_review_required", nullable = false)
    @Builder.Default
    private Boolean humanReviewRequired = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> reasonCodes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> explanation = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_refs", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> evidenceRefs = new ArrayList<>();

    @Column(name = "confidence", precision = 8, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limitations", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Object> limitations = new ArrayList<>();

    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private String status = RecommendationStatus.RECOMMENDED.name();

    @Column(name = "authoritative", nullable = false)
    @Builder.Default
    private Boolean authoritative = false;

    @Column(name = "deterministic_decision_hash", length = 128)
    private String deterministicDecisionHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimensions", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> dimensions = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /** In-memory child results (not always hydrated from DB in P2 shadow path). */
    @Transient
    @Builder.Default
    private List<CiLimitMethodResult> limitMethodResults = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<CiPricingComponentResult> pricingComponentResults = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<CiConditionRecommendation> conditionRecommendations = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<CiCovenantRecommendation> covenantRecommendations = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<CiPolicyDeviation> deviations = new ArrayList<>();

    @Transient
    @Builder.Default
    private Map<String, Object> authorityDetail = new LinkedHashMap<>();
}
