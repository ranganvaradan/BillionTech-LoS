package com.los.core.repository;

import com.los.core.model.entity.WorkflowConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowConfigRepository extends JpaRepository<WorkflowConfig, UUID> {

    Optional<WorkflowConfig> findByBorrowerTypeAndLoanProductAndActiveTrue(String borrowerType, String loanProduct);

    List<WorkflowConfig> findByActiveTrue();
}
