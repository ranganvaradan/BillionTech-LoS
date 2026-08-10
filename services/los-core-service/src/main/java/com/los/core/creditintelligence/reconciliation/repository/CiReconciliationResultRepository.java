package com.los.core.creditintelligence.reconciliation.repository;

import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiReconciliationResultRepository extends JpaRepository<CiReconciliationResult, UUID> {

    List<CiReconciliationResult> findByApplicationIdOrderByExecutedAtDesc(UUID applicationId);

    List<CiReconciliationResult> findByApplicationIdAndReconciliationCodeOrderByExecutedAtDesc(
            UUID applicationId, String reconciliationCode);

    Optional<CiReconciliationResult> findFirstByApplicationIdAndReconciliationCodeOrderByExecutedAtDesc(
            UUID applicationId, String reconciliationCode);

    List<CiReconciliationResult> findByEvaluationId(UUID evaluationId);

    List<CiReconciliationResult> findByFactSnapshotId(UUID factSnapshotId);
}
