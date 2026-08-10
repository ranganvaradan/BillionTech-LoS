package com.los.core.creditintelligence.core.repository;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiMetricResultRepository extends JpaRepository<CiMetricResult, UUID> {

    List<CiMetricResult> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<CiMetricResult> findByBureauReportId(UUID bureauReportId);

    Optional<CiMetricResult> findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
            UUID applicationId, String metricCode);

    Optional<CiMetricResult> findFirstByBureauReportIdAndMetricCodeOrderByCreatedAtDesc(
            UUID bureauReportId, String metricCode);

    List<CiMetricResult> findByIdIn(Collection<UUID> ids);
}
