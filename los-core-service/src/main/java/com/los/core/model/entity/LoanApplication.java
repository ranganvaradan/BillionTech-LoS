package com.los.core.model.entity;

import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeOwner;
import com.los.core.model.enums.IntakeSegment;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.core.model.enums.VkycCompletionMode;
import com.los.core.model.enums.VkycStatus;
import com.los.core.service.loan.LoanApplicationStatusHistoryListener;
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
@Table(name = "loan_applications")
@EntityListeners(LoanApplicationStatusHistoryListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String applicationNumber;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BorrowerType borrowerType;

    @Column(nullable = false, length = 50)
    private String loanProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "intake_segment", nullable = false, length = 20)
    @Builder.Default
    private IntakeSegment intakeSegment = IntakeSegment.BORROWER;

    @Column(precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column
    private Integer tenureMonths;

    /** Encore LMS product code for this application (overrides workflow default). */
    @Column(name = "lms_product_code", length = 50)
    private String lmsProductCode;

    /** Encore tenure unit for this application (Day, Month, Week). */
    @Column(name = "lms_tenure_unit", length = 20)
    private String lmsTenureUnit;

    /**
     * Resolved Workflow Version identity ({@code workflow_configs.id}).
     * Once set, orchestration must use this Version only (W1).
     */
    @Column(name = "workflow_id")
    private UUID workflowId;

    /** Snapshot of {@code workflow_configs.version} at resolution time (W1). */
    @Column(name = "workflow_version")
    private Integer workflowVersion;

    /** EXPLICIT | DEFAULT | LEGACY_EXISTING | MIGRATED (W1). */
    @Column(name = "workflow_resolution_source", length = 40)
    private String workflowResolutionSource;

    @Column(name = "workflow_resolved_at")
    private Instant workflowResolvedAt;

    /** SHA-256 of workflow definition at resolve time; detects in-place mutation (W1). */
    @Column(name = "workflow_content_hash", length = 64)
    private String workflowContentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    /**
     * Snapshot of status after load / last history write — used by
     * {@link LoanApplicationStatusHistoryListener} to detect transitions.
     */
    @Transient
    private ApplicationStatus loadedStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> personalInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> businessInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> financialInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> collateralInfo;

    @Column(length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "intake_owner", nullable = false, length = 20)
    @Builder.Default
    private IntakeOwner intakeOwner = IntakeOwner.STAFF;

    @Column(name = "intake_completed_step")
    private Integer intakeCompletedStep;

    @Column(name = "borrower_sent_back_notes", columnDefinition = "TEXT")
    private String borrowerSentBackNotes;

    private UUID assignedTo;

    private Instant slaDeadline;

    private Instant currentStepStartedAt;

    @Builder.Default
    private boolean escalated = false;

    private Instant escalatedAt;

    // --- Flow orchestration fields (V6 migration) ---

    @Column(precision = 15, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(precision = 5, scale = 2)
    private BigDecimal approvedRate;

    @Column(precision = 15, scale = 2)
    private BigDecimal disbursedAmount;

    private Instant disbursedAt;

    @Column(length = 100)
    private String lmsReferenceId;

    @Column(name = "sub_program_id")
    private UUID subProgramId;

    @Column(name = "plp_borrower_id")
    private UUID plpBorrowerId;

    @Column(name = "plp_sub_program_borrower_id")
    private UUID plpSubProgramBorrowerId;

    @Column(name = "plp_borrower_program_mapping_id")
    private UUID plpBorrowerProgramMappingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plp_program_sync_status", nullable = false, length = 20)
    @Builder.Default
    private PlpSyncStatus plpProgramSyncStatus = PlpSyncStatus.NOT_SYNCED;

    @Column(name = "plp_program_sync_error", length = 500)
    private String plpProgramSyncError;

    @Column(name = "plp_program_synced_at")
    private Instant plpProgramSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "plp_borrower_sync_status", nullable = false, length = 30)
    @Builder.Default
    private PlpSyncStatus plpBorrowerSyncStatus = PlpSyncStatus.NOT_SYNCED;

    @Column(name = "plp_borrower_synced_at")
    private Instant plpBorrowerSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "plp_link_sync_status", nullable = false, length = 30)
    @Builder.Default
    private PlpSyncStatus plpLinkSyncStatus = PlpSyncStatus.NOT_SYNCED;

    @Column(name = "plp_link_synced_at")
    private Instant plpLinkSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "plp_mapping_sync_status", nullable = false, length = 30)
    @Builder.Default
    private PlpSyncStatus plpMappingSyncStatus = PlpSyncStatus.NOT_SYNCED;

    @Column(name = "plp_mapping_synced_at")
    private Instant plpMappingSyncedAt;

    /**
     * Encore customer / party id once idempotent LMS customer onboarding is implemented (nullable).
     */
    @Column(name = "lms_encore_customer_id", length = 100)
    private String lmsEncoreCustomerId;

    @Column(length = 100)
    private String esignTransactionId;

    private Boolean vkycRequired;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private VkycStatus vkycStatus;

    private Instant vkycCompletedAt;

    private UUID vkycAgentId;

    private UUID vkycAuditorId;

    @Column(length = 150)
    private String vkycReferenceId;

    @Column(columnDefinition = "text")
    private String vkycUrl;
    @Column(length = 120)
    private String vkycTransactionId;

    private Instant vkycUrlGeneratedAt;

    private Instant vkycUrlExpiryAt;

    private Instant vkycLastResentAt;

    private Integer vkycResendCount;

    private Boolean vkycEmailSent;

    private Instant vkycEmailSentAt;

    private UUID vkycGeneratedBy;
    @Column(length = 80)
    private String vkycLastEvent;
    @Column(columnDefinition = "text")
    private String vkycEventPayload;
    @Column(columnDefinition = "text")
    private String vkycResultPayload;
    @Column(length = 200)
    private String vkycAgentName;
    private Instant vkycAgentUpdatedOn;
    private Instant vkycCompletedOn;
    @Column(columnDefinition = "text")
    private String vkycVideoUrl;
    @Column(columnDefinition = "text")
    private String vkycPanImageUrl;
    @Column(columnDefinition = "text")
    private String vkycFaceImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private VkycCompletionMode vkycCompletionMode;

    @Column(length = 80)
    private String pkycReason;

    @Column(columnDefinition = "text")
    private String pkycComments;

    private UUID pkycDocumentId;

    private UUID pkycVerifiedBy;

    private Instant pkycVerifiedAt;

    private Boolean amlHit;

    private Integer bureauScore;

    private Integer manualBureauScore;

    @Column(columnDefinition = "text")
    private String manualBureauRemarks;

    private UUID manualBureauDocumentId;

    @Column(length = 30)
    private String creditDecision;

    private Integer creditRiskScore;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant submittedAt;
}
