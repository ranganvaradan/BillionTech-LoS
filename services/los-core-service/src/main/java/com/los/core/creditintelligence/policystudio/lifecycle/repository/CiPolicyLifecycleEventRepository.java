package com.los.core.creditintelligence.policystudio.lifecycle.repository;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiPolicyLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyLifecycleEventRepository extends JpaRepository<CiPolicyLifecycleEvent, UUID> {

    List<CiPolicyLifecycleEvent> findByApplicabilityIdOrderByCreatedAtDesc(UUID applicabilityId);

    List<CiPolicyLifecycleEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
