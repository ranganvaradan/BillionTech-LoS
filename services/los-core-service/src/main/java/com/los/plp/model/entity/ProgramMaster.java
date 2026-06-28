package com.los.plp.model.entity;

import com.los.plp.model.enums.PlpSyncStatus;
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
