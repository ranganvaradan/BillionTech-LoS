package com.los.core.creditintelligence.policy.repository;

import com.los.core.creditintelligence.policy.domain.CiPolicyStageResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyStageResultRepository extends JpaRepository<CiPolicyStageResult, UUID> {

    List<CiPolicyStageResult> findByEvaluationIdOrderBySequenceAsc(UUID evaluationId);
}
