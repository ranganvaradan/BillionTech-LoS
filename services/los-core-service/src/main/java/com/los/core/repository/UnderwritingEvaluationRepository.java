package com.los.core.repository;

import com.los.core.model.entity.UnderwritingEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnderwritingEvaluationRepository extends JpaRepository<UnderwritingEvaluation, UUID> {

    Optional<UnderwritingEvaluation> findTopByApplicationIdOrderByEvaluatedAtDesc(UUID applicationId);
}
