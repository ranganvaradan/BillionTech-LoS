package com.los.core.creditintelligence.reconciliation.repository;

import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiReconciliationEvidenceRepository extends JpaRepository<CiReconciliationEvidence, UUID> {

    List<CiReconciliationEvidence> findByReconciliationResultId(UUID reconciliationResultId);

    List<CiReconciliationEvidence> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
