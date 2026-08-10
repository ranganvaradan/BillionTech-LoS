package com.los.core.creditintelligence.policy.repository;

import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyEvaluationRepository extends JpaRepository<CiPolicyEvaluation, UUID> {

    List<CiPolicyEvaluation> findByEvaluationContextId(UUID evaluationContextId);

    List<CiPolicyEvaluation> findByPolicyPackageId(UUID policyPackageId);
}
