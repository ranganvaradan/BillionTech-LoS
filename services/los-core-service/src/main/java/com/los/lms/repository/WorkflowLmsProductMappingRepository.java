package com.los.lms.repository;

import com.los.lms.entity.WorkflowLmsProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowLmsProductMappingRepository extends JpaRepository<WorkflowLmsProductMapping, UUID> {

    Optional<WorkflowLmsProductMapping> findByBorrowerTypeAndLoanProduct(String borrowerType, String loanProduct);

    Optional<WorkflowLmsProductMapping> findByPartnerCodeAndLoanProduct(String partnerCode, String loanProduct);
}
