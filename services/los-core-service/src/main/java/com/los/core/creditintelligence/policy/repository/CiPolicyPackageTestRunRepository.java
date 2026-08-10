package com.los.core.creditintelligence.policy.repository;

import com.los.core.creditintelligence.policy.domain.CiPolicyPackageTestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiPolicyPackageTestRunRepository extends JpaRepository<CiPolicyPackageTestRun, UUID> {

    List<CiPolicyPackageTestRun> findByPackageIdOrderByRanAtDesc(UUID packageId);
}
