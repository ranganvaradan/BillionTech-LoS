package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanonicalApplicationConfigurationRepository
        extends JpaRepository<CanonicalApplicationConfigurationEntity, UUID> {

    Optional<CanonicalApplicationConfigurationEntity> findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(
            UUID applicationId, String status);

    List<CanonicalApplicationConfigurationEntity> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
