package com.los.core.creditintelligence.decision.repository;

import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiDecisionStrategyRepository extends JpaRepository<CiDecisionStrategy, UUID> {

    List<CiDecisionStrategy> findByTenantId(UUID tenantId);

    Optional<CiDecisionStrategy> findByTenantIdAndStrategyCodeAndVersion(
            UUID tenantId, String strategyCode, String version);
}
