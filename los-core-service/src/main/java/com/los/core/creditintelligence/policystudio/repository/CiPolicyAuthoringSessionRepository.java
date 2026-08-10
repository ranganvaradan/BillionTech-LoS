package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAuthoringSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiPolicyAuthoringSessionRepository extends JpaRepository<CiPolicyAuthoringSession, UUID> {
    Optional<CiPolicyAuthoringSession> findByPolicyDocumentId(UUID policyDocumentId);

    List<CiPolicyAuthoringSession> findByTenantIdOrderByLastUpdatedAtDesc(UUID tenantId);
}
