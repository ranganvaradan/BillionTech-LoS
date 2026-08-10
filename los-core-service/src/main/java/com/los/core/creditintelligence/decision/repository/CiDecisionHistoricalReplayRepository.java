package com.los.core.creditintelligence.decision.repository;

import com.los.core.creditintelligence.decision.domain.CiDecisionHistoricalReplay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CiDecisionHistoricalReplayRepository extends JpaRepository<CiDecisionHistoricalReplay, UUID> {
}
