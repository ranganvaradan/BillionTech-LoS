package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyRuleCandidateRepository extends JpaRepository<CiPolicyRuleCandidate, UUID> {
    List<CiPolicyRuleCandidate> findByClauseId(UUID clauseId);
}