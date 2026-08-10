package com.los.core.repository;

import com.los.core.model.entity.WorkflowConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowConfigRepository extends JpaRepository<WorkflowConfig, UUID> {

    /**
     * Active workflows for a resolution key, highest version first.
     * Multiple active rows for the same key are allowed (see V84).
     */
    List<WorkflowConfig> findByBorrowerTypeAndLoanProductAndIntakeSegmentAndActiveTrueOrderByVersionDesc(
            String borrowerType, String loanProduct, String intakeSegment);

    List<WorkflowConfig> findByActiveTrue();

}
