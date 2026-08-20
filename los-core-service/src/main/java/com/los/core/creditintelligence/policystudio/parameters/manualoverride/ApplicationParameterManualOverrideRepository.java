package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationParameterManualOverrideRepository
        extends JpaRepository<ApplicationParameterManualOverride, UUID> {

    List<ApplicationParameterManualOverride> findByApplicationIdAndActiveTrue(UUID applicationId);

    Optional<ApplicationParameterManualOverride> findByApplicationIdAndCanonicalParameterIdAndActiveTrue(
            UUID applicationId, String canonicalParameterId);

    List<ApplicationParameterManualOverride> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
