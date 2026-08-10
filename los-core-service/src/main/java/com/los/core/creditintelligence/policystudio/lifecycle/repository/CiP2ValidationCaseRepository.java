package com.los.core.creditintelligence.policystudio.lifecycle.repository;

import com.los.core.creditintelligence.policystudio.lifecycle.domain.CiP2ValidationCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiP2ValidationCaseRepository extends JpaRepository<CiP2ValidationCase, UUID> {
    List<CiP2ValidationCase> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
