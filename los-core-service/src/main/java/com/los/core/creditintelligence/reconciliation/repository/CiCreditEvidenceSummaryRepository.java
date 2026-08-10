package com.los.core.creditintelligence.reconciliation.repository;

import com.los.core.creditintelligence.reconciliation.domain.CiCreditEvidenceSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiCreditEvidenceSummaryRepository extends JpaRepository<CiCreditEvidenceSummary, UUID> {

    List<CiCreditEvidenceSummary> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiCreditEvidenceSummary> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<CiCreditEvidenceSummary> findFirstByEvaluationId(UUID evaluationId);
}
