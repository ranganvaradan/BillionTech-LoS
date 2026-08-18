package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CanonicalShadowEvaluationRepository
        extends JpaRepository<CanonicalShadowEvaluationEntity, UUID> {

    Optional<CanonicalShadowEvaluationEntity> findFirstByUnderwritingEvaluationIdAndIdentityHash(
            UUID underwritingEvaluationId, String identityHash);

    Optional<CanonicalShadowEvaluationEntity> findFirstByApplicationIdAndIdentityHashAndUnderwritingEvaluationIdIsNull(
            UUID applicationId, String identityHash);

    List<CanonicalShadowEvaluationEntity> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    long countByApplicationId(UUID applicationId);
}
