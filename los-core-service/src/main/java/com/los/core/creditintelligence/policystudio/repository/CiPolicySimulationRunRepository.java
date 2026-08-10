package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicySimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicySimulationRunRepository extends JpaRepository<CiPolicySimulationRun, UUID> {
    List<CiPolicySimulationRun> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
