package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyTestCaseRepository extends JpaRepository<CiPolicyTestCase, UUID> {
    List<CiPolicyTestCase> findByRuleCandidateId(UUID ruleCandidateId);
}