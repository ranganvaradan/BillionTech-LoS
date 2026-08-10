package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiEvaluationStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiEvaluationStageRepository extends JpaRepository<CiEvaluationStage, UUID> {

    List<CiEvaluationStage> findByEvaluationIdOrderBySequenceAsc(UUID evaluationId);
}
