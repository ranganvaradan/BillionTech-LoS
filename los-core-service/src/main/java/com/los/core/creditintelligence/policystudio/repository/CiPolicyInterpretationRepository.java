package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyInterpretationRepository extends JpaRepository<CiPolicyInterpretation, UUID> {
    List<CiPolicyInterpretation> findByClauseIdOrderByInterpretationVersionDesc(UUID clauseId);
}