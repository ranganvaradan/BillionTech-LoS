package com.los.core.creditintelligence.decisionpolicy.corpus.repository;

import com.los.core.creditintelligence.decisionpolicy.corpus.domain.CiDpV1ValidationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiDpV1ValidationRunRepository extends JpaRepository<CiDpV1ValidationRun, UUID> {
    List<CiDpV1ValidationRun> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
}
