package com.los.core.customercategory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "customer_category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCategoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfigLifecycleStatus status;

    @Column(name = "borrower_type", nullable = false, length = 32)
    private String borrowerType;

    @Column(name = "loan_product", nullable = false, length = 80)
    private String loanProduct;

    @Column(name = "intake_segment", nullable = false, length = 32)
    private String intakeSegment;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    /**
     * Transitional / internal Policy Set package. Nullable after STEP-2.
     * Not the lender-facing principal underwriting relation.
     */
    @Column(name = "policy_set_id")
    private UUID policySetId;

    /** Principal bind: Policy Studio catalogue row (exact Policy Version). */
    @Column(name = "policy_applicability_id")
    private UUID policyApplicabilityId;

    /** Policy Studio document identity for the bound version. */
    @Column(name = "policy_document_id")
    private UUID policyDocumentId;

    /** Exact Policy Version label pinned at bind time. */
    @Column(name = "policy_version_label", length = 40)
    private String policyVersionLabel;

    /** Stable Policy lineage handle across versions (optional). */
    @Column(name = "policy_lineage_id")
    private UUID policyLineageId;

    /**
     * W2 — exact Workflow Version ({@code workflow_configs.id}). Independent of Policy bind.
     * At most one Workflow Version per Category Version. Config only — not live routing.
     */
    @Column(name = "workflow_id")
    private UUID workflowId;

    /** Denormalized {@code workflow_configs.version} at bind time. */
    @Column(name = "workflow_version")
    private Integer workflowVersion;

    /** {@link com.los.core.service.workflow.WorkflowContentHash} snapshot at bind (mutation detection). */
    @Column(name = "workflow_content_hash", length = 64)
    private String workflowContentHash;

    /** Denormalized Workflow display name at bind time. */
    @Column(name = "workflow_name", length = 100)
    private String workflowName;

    @Column(name = "seed_source_rule_set_id")
    private UUID seedSourceRuleSetId;

    @Column(name = "review_status", length = 40)
    private String reviewStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inference_notes", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> inferenceNotes = new LinkedHashMap<>();

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "submitted_by", length = 120)
    private String submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "activated_by", length = 120)
    private String activatedBy;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_by", length = 120)
    private String retiredBy;

    @Column(name = "retirement_reason", columnDefinition = "TEXT")
    private String retirementReason;

    @Column(name = "reason_for_change", columnDefinition = "TEXT")
    private String reasonForChange;

    @Column(name = "replaces_category_id")
    private UUID replacesCategoryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "governance_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> governanceJson = new LinkedHashMap<>();

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (inferenceNotes == null) {
            inferenceNotes = new LinkedHashMap<>();
        }
        if (governanceJson == null) {
            governanceJson = new LinkedHashMap<>();
        }
    }
}
