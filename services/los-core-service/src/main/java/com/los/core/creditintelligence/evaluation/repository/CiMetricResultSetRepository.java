package com.los.core.creditintelligence.evaluation.repository;

import com.los.core.creditintelligence.evaluation.domain.CiMetricResultSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiMetricResultSetRepository extends JpaRepository<CiMetricResultSet, UUID> {

    List<CiMetricResultSet> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
