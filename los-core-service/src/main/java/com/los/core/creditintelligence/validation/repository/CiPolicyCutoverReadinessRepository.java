package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiPolicyCutoverReadiness;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyCutoverReadinessRepository extends JpaRepository<CiPolicyCutoverReadiness, UUID> {
    List<CiPolicyCutoverReadiness> findByTenantIdOrderByAssessedAtDesc(UUID tenantId);

    Optional<CiPolicyCutoverReadiness> findFirstByTenantIdOrderByAssessedAtDesc(UUID tenantId);
}
