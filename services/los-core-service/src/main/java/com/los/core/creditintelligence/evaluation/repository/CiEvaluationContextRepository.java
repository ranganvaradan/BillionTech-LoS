package com.los.core.creditintelligence.evaluation.repository;

import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CiEvaluationContextRepository extends JpaRepository<CiEvaluationContext, UUID> {

    Optional<CiEvaluationContext> findByTenantIdAndApplicationIdAndContentHash(
            UUID tenantId, UUID applicationId, String contentHash);

    Optional<CiEvaluationContext> findById(UUID id);

    Optional<CiEvaluationContext> findTopByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
