package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiPolicyComparison;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyComparisonRepository extends JpaRepository<CiPolicyComparison, UUID> {
    List<CiPolicyComparison> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<CiPolicyComparison> findByValidationRunId(UUID validationRunId);
}
