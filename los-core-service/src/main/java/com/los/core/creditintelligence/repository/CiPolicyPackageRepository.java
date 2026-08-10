package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiPolicyPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CiPolicyPackageRepository extends JpaRepository<CiPolicyPackage, UUID> {

    Optional<CiPolicyPackage> findByTenantIdAndProductCodeAndPolicyIdentifier(
            UUID tenantId, String productCode, String policyIdentifier);
}
