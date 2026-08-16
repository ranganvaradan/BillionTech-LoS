package com.los.core.creditintelligence.policystudio.certification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiProductionCertificationRepository extends JpaRepository<CiProductionCertification, UUID> {

    Optional<CiProductionCertification> findByArtifactTypeAndArtifactIdAndArtifactVersionAndScopeTypeAndScopeId(
            String artifactType, String artifactId, String artifactVersion, String scopeType, String scopeId);

    List<CiProductionCertification> findByArtifactTypeAndStatus(String artifactType, String status);
}
