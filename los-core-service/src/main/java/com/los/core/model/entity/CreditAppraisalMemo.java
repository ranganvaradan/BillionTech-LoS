package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "credit_appraisal_memos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditAppraisalMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cam_json", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> camJson = Map.of();

    @Column(columnDefinition = "text")
    private String observations;

    @Column(name = "risk_assessment", columnDefinition = "text")
    private String riskAssessment;

    @Column(columnDefinition = "text")
    private String mitigants;

    @Column(name = "recommended_decision", length = 30)
    private String recommendedDecision;

    @Column(name = "cam_reviewed", nullable = false)
    @Builder.Default
    private boolean camReviewed = false;

    @Column(name = "cam_version", nullable = false)
    @Builder.Default
    private Integer camVersion = 1;

    /** DRAFT | SUBMITTED | APPROVED | REJECTED | SENT_BACK */
    @Column(name = "cam_status", nullable = false, length = 30)
    @Builder.Default
    private String camStatus = "DRAFT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "editable_sections_json", columnDefinition = "jsonb")
    private Map<String, Object> editableSectionsJson;

    @Column(name = "recommended_amount", precision = 15, scale = 2)
    private java.math.BigDecimal recommendedAmount;

    @Column(name = "recommended_tenure_months")
    private Integer recommendedTenureMonths;

    @Column(name = "recommended_rate", precision = 5, scale = 2)
    private BigDecimal recommendedRate;

    /** UPFRONT | REDUCING */
    @Column(name = "interest_type", length = 20)
    private String interestType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_precedent_json", columnDefinition = "jsonb")
    private List<String> conditionsPrecedentJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_subsequent_json", columnDefinition = "jsonb")
    private List<String> conditionsSubsequentJson;

    @Column(name = "credit_officer_remarks", columnDefinition = "text")
    private String creditOfficerRemarks;

    @Column(name = "credit_manager_remarks", columnDefinition = "text")
    private String creditManagerRemarks;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cam_pdf_path", length = 500)
    private String camPdfPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
