package com.los.core.creditintelligence.decision.repository;

import com.los.core.creditintelligence.decision.domain.CiHumanCreditDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Exists for future handoff. P2 must not write via engine paths. */
public interface CiHumanCreditDecisionRepository extends JpaRepository<CiHumanCreditDecision, UUID> {
}
