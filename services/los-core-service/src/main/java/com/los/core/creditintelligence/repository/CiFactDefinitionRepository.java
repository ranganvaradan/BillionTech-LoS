package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiFactDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiFactDefinitionRepository extends JpaRepository<CiFactDefinition, UUID> {

    Optional<CiFactDefinition> findByCanonicalPathAndVersion(String canonicalPath, Integer version);

    List<CiFactDefinition> findByStatus(String status);
}
