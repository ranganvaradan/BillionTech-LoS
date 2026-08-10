package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiCreditEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiCreditEvaluationRepository extends JpaRepository<CiCreditEvaluation, UUID> {

    List<CiCreditEvaluation> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiCreditEvaluation> findByIdAndTenantId(UUID id, UUID tenantId);
}
