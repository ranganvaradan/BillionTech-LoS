package com.los.core.creditintelligence.decision.repository;

import com.los.core.creditintelligence.decision.domain.CiDecisionInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiDecisionInputRepository extends JpaRepository<CiDecisionInput, UUID> {

    List<CiDecisionInput> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);
}
