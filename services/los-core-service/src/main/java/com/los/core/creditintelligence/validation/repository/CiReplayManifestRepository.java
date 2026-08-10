package com.los.core.creditintelligence.validation.repository;

import com.los.core.creditintelligence.validation.domain.CiReplayManifest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiReplayManifestRepository extends JpaRepository<CiReplayManifest, UUID> {
    Optional<CiReplayManifest> findFirstByEvaluationContextIdOrderByCreatedAtDesc(UUID evaluationContextId);

    List<CiReplayManifest> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
