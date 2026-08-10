package com.los.core.creditintelligence.evaluation.repository;

import com.los.core.creditintelligence.evaluation.domain.CiReconciliationResultSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiReconciliationResultSetRepository extends JpaRepository<CiReconciliationResultSet, UUID> {

    List<CiReconciliationResultSet> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
