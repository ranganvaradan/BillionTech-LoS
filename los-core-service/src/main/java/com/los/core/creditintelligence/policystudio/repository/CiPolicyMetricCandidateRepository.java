package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyMetricCandidateRepository extends JpaRepository<CiPolicyMetricCandidate, UUID> {
    List<CiPolicyMetricCandidate> findByClauseId(UUID clauseId);
}