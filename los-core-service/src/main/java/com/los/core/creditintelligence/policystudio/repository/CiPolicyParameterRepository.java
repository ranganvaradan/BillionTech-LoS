package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyParameterRepository extends JpaRepository<CiPolicyParameter, UUID> {
    List<CiPolicyParameter> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<CiPolicyParameter> findByTenantIdAndCodeAndProductScopeAndVersion(
            UUID tenantId, String code, String productScope, Integer version);
}
