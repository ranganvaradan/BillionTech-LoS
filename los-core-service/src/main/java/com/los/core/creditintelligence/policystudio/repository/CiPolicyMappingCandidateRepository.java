package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyMappingCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyMappingCandidateRepository extends JpaRepository<CiPolicyMappingCandidate, UUID> {
    List<CiPolicyMappingCandidate> findByInterpretationIdOrderByRankAsc(UUID interpretationId);
}