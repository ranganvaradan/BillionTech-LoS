package com.los.core.creditintelligence.policy.repository;

import com.los.core.creditintelligence.policy.domain.CiPolicyHistoricalReplay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CiPolicyHistoricalReplayRepository extends JpaRepository<CiPolicyHistoricalReplay, UUID> {
}
