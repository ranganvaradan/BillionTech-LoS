package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiKycPolicyEvaluationRepository extends JpaRepository<CiKycPolicyEvaluation, UUID> {

    List<CiKycPolicyEvaluation> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiKycPolicyEvaluation> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiKycPolicyEvaluation> findByDeterministicHash(String deterministicHash);
}
