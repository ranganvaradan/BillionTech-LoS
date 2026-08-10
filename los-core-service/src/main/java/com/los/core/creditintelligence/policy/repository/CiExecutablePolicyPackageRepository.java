package com.los.core.creditintelligence.policy.repository;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiExecutablePolicyPackageRepository extends JpaRepository<CiExecutablePolicyPackage, UUID> {

    List<CiExecutablePolicyPackage> findByTenantIdOrderByPublishedAtDesc(UUID tenantId);

    Optional<CiExecutablePolicyPackage> findByTenantIdAndPolicyCodeAndVersion(
            UUID tenantId, String policyCode, String version);

    List<CiExecutablePolicyPackage> findByTenantIdAndStatus(UUID tenantId, String status);
}
