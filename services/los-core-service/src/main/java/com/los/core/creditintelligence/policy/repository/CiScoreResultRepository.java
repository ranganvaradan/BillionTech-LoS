package com.los.core.creditintelligence.policy.repository;

import com.los.core.creditintelligence.policy.domain.CiScoreResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiScoreResultRepository extends JpaRepository<CiScoreResult, UUID> {

    List<CiScoreResult> findByEvaluationId(UUID evaluationId);
}
