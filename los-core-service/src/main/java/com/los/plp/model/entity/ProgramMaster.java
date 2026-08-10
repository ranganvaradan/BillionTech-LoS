package com.los.plp.model.entity;

import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.model.enums.ProgramApprovalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "program_masters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "program_code", nullable = false, unique = true, length = 50)
    private String programCode;

    @Column(name = "program_name", nullable = false, length = 200)
    private String programName;

    @Column(name = "product_type", nullable = false, length = 50)
    private String productType;

    @Column(name = "program_limit", precision = 15, scale = 2)
    private BigDecimal programLimit;

    @Column(name = "max_borrower_limit", precision = 15, scale = 2)
    private BigDecimal maxBorrowerLimit;

    @Column(name = "interest_rate", precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "tenure_days")
    private Integer tenureDays;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "validity_start_date")
    private LocalDate validityStartDate;

    @Column(name = "validity_end_date")
    private LocalDate validityEndDate;

    @Column(name = "workflow_config_id")
    private UUID workflowConfigId;

    @Column(name = "plp_lender_id", nullable = false)
    private UUID plpLenderId;

    @Column(name = "plp_program_id")
    private UUID plpProgramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plp_program_sync_status", nullable = false, length = 20)
    @Builder.Default
    private PlpSyncStatus plpProgramSyncStatus = PlpSyncStatus.NOT_SYNCED;

    @Column(name = "plp_program_sync_error", length = 500)
    private String plpProgramSyncError;

    @Column(name = "plp_program_synced_at")
    private Instant plpProgramSyncedAt;

    /** YES = Encore LMS; NO = internal account only (bl-core lms_entry_in). */
    @Column(name = "lms_entry_in", nullable = false, length = 3)
    @Builder.Default
    private String lmsEntryIn = "NO";

    @Column(name = "encore_product_code", length = 50)
    private String encoreProductCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    @Builder.Default
    private ProgramApprovalStatus approvalStatus = ProgramApprovalStatus.DRAFT;

    @Column(name = "assigned_l1_user_id")
    private UUID assignedL1UserId;

    @Column(name = "assigned_l2_user_id")
    private UUID assignedL2UserId;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "approval_history_json", columnDefinition = "TEXT")
    private String approvalHistoryJson;

    @Column(name = "anchor_application_id")
    private UUID anchorApplicationId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    /** Last known PLP program status (DRAFT, ACTIVE, etc.) mirrored from PLP integration. */
    @Column(name = "plp_operational_status", length = 32)
    private String plpOperationalStatus;

    /** Minimum dependency on anchor (% of business) required for borrower eligibility. */
    @Column(name = "dependency_vintage_percent", precision = 8, scale = 2)
    private BigDecimal dependencyVintagePercent;

    /** Minimum anchor relationship vintage in months required for borrower eligibility. */
    @Column(name = "anchor_relationship_vintage_months")
    private Integer anchorRelationshipVintageMonths;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
