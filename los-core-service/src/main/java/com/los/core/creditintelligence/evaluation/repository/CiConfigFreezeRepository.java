package com.los.core.creditintelligence.evaluation.repository;

import com.los.core.creditintelligence.evaluation.domain.CiConfigFreeze;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CiConfigFreezeRepository extends JpaRepository<CiConfigFreeze, UUID> {

    Optional<CiConfigFreeze> findByTenantIdAndContentHash(UUID tenantId, String contentHash);
}
