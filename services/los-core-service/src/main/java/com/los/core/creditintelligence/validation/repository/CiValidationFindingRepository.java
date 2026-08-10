package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiValidationFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiValidationFindingRepository extends JpaRepository<CiValidationFinding, UUID> {
    List<CiValidationFinding> findByValidationRunIdOrderByCreatedAtAsc(UUID validationRunId);
}
