package com.los.core.creditintelligence.policystudio.lifecycle.repository;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiP2ValidationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiP2ValidationRunRepository extends JpaRepository<CiP2ValidationRun, UUID> {
    List<CiP2ValidationRun> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
}
