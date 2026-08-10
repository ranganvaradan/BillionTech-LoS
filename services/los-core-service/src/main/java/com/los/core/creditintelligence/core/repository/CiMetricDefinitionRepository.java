package com.los.core.creditintelligence.core.repository;

import com.los.core.creditintelligence.core.domain.CiMetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CiMetricDefinitionRepository extends JpaRepository<CiMetricDefinition, UUID> {

    Optional<CiMetricDefinition> findByMetricCodeAndVersion(String metricCode, String version);
}
