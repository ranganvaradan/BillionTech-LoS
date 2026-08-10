package com.los.core.creditintelligence.policy.repository;

import com.los.core.creditintelligence.policy.domain.CiPolicyPackageDiff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CiPolicyPackageDiffRepository extends JpaRepository<CiPolicyPackageDiff, UUID> {
}
