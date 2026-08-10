package com.los.core.creditintelligence.policystudio.lifecycle.repository;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyShadowRouting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyShadowRoutingRepository extends JpaRepository<CiPolicyShadowRouting, UUID> {

    List<CiPolicyShadowRouting> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<CiPolicyShadowRouting> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countBySelectedApplicabilityId(UUID applicabilityId);
}
