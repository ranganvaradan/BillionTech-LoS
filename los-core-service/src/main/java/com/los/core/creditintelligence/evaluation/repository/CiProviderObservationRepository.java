package com.los.core.creditintelligence.evaluation.repository;

import com.los.core.creditintelligence.evaluation.domain.CiProviderObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiProviderObservationRepository extends JpaRepository<CiProviderObservation, UUID> {

    List<CiProviderObservation> findByApplicationIdAndProvider(UUID applicationId, String provider);
}
