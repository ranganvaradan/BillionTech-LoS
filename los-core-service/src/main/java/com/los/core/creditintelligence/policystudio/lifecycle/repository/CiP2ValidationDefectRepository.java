package com.los.core.creditintelligence.policystudio.lifecycle.repository;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiP2ValidationDefect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiP2ValidationDefectRepository extends JpaRepository<CiP2ValidationDefect, UUID> {
    List<CiP2ValidationDefect> findByRunIdOrderByCreatedAtDesc(UUID runId);
    long countByRunIdAndBlockingIsTrueAndResolvedIsFalse(UUID runId);
}
