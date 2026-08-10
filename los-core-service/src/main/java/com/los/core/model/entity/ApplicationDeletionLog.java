package com.los.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "application_deletion_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "application_number", nullable = false, length = 30)
    private String applicationNumber;

    @Column(name = "loan_product", nullable = false, length = 50)
    private String loanProduct;

    @Column(name = "intake_segment", nullable = false, length = 20)
    private String intakeSegment;

    @Column(name = "application_status", nullable = false, length = 30)
    private String applicationStatus;

    @Column(name = "borrower_user_id")
    private UUID borrowerUserId;

    @Column(name = "borrower_email", length = 200)
    private String borrowerEmail;

    @Column(name = "plp_borrower_id")
    private UUID plpBorrowerId;

    @Column(name = "deleted_by_user_id")
    private UUID deletedByUserId;

    @Column(name = "deleted_by_email", length = 200)
    private String deletedByEmail;

    @Column(name = "deleted_by_role", length = 50)
    private String deletedByRole;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "plp_cleanup_attempted", nullable = false)
    @Builder.Default
    private boolean plpCleanupAttempted = false;

    @Column(name = "plp_cleanup_summary", columnDefinition = "TEXT")
    private String plpCleanupSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "jsonb")
    private Map<String, Object> snapshotJson;

    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;
}
