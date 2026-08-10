package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiSourceArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiSourceArtifactRepository extends JpaRepository<CiSourceArtifact, UUID> {

    List<CiSourceArtifact> findBySourceRecordId(UUID sourceRecordId);
}
