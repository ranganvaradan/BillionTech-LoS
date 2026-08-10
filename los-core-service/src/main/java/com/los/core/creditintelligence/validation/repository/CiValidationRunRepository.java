package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiValidationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiValidationRunRepository extends JpaRepository<CiValidationRun, UUID> {
    List<CiValidationRun> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<CiValidationRun> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CiValidationRun> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
