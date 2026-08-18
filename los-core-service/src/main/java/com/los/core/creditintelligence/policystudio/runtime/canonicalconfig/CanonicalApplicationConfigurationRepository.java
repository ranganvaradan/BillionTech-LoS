package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CanonicalApplicationConfigurationRepository
        extends JpaRepository<CanonicalApplicationConfigurationEntity, UUID> {

    Optional<CanonicalApplicationConfigurationEntity> findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(
            UUID applicationId, String status);

    List<CanonicalApplicationConfigurationEntity> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
