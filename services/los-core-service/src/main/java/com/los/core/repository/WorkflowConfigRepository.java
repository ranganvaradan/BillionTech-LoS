package com.los.core.repository;

import com.los.core.model.entity.WorkflowConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowConfigRepository extends JpaRepository<WorkflowConfig, UUID> {

    Optional<WorkflowConfig> findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrue(
            String borrowerType, String loanProduct, String intakeSegment);

    List<WorkflowConfig> findByActiveTrue();

    /**
     * Deactivates other active workflows for the same resolution key before activating a new one.
     * Uses a bulk UPDATE so PostgreSQL never sees two active rows for the same tuple during flush.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE WorkflowConfig w
            SET w.active = false, w.updatedAt = :updatedAt
            WHERE w.borrowerType = :borrowerType
              AND w.loanProduct = :loanProduct
              AND w.intakeSegment = :intakeSegment
              AND w.active = true
              AND w.id <> :workflowId
            """)
    int deactivateOtherActiveWorkflows(
            @Param("borrowerType") String borrowerType,
            @Param("loanProduct") String loanProduct,
            @Param("intakeSegment") String intakeSegment,
            @Param("workflowId") UUID workflowId,
            @Param("updatedAt") Instant updatedAt);
}
