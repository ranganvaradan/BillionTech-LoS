package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiPolicyBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyBindingRepository extends JpaRepository<CiPolicyBinding, UUID> {
    List<CiPolicyBinding> findByTenantId(UUID tenantId);

    Optional<CiPolicyBinding> findByTenantIdAndLegacyParameter(UUID tenantId, String legacyParameter);

    long countByTenantId(UUID tenantId);
}
