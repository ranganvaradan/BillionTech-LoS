package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiLenderIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiLenderIdentityRepository extends JpaRepository<CiLenderIdentity, UUID> {
    List<CiLenderIdentity> findByTenantId(UUID tenantId);

    Optional<CiLenderIdentity> findByTenantIdAndCanonicalNameIgnoreCase(UUID tenantId, String canonicalName);
}
