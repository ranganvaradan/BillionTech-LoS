package com.los.plp.model.entity;

import com.los.plp.model.enums.PlpSyncStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sub_program_masters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubProgramMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sub_program_code", nullable = false, unique = true, length = 50)
    private String subProgramCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "anchor_id", nullable = false)
    private UUID anchorId;

    @Column(name = "flow_type", length = 50)
    private String flowType;

    @Column(name = "anchor_role", length = 50)
    private String anchorRole;

    @Column(name = "borrower_role", length = 50)
    private String borrowerRole;

    @Column(name = "sub_program_limit", precision = 15, scale = 2)
    private BigDecimal subProgramLimit;

    @Column(name = "plp_sub_program_id")
    private UUID plpSubProgramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plp_sub_program_sync_status", nullable = false, length = 20)
    @Builder.Default
    private PlpSyncStatus plpSubProgramSyncStatus = PlpSyncStatus.NOT_SYNCED;

    @Column(name = "plp_sub_program_sync_error", length = 500)
    private String plpSubProgramSyncError;

    @Column(name = "plp_sub_program_synced_at")
    private Instant plpSubProgramSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
