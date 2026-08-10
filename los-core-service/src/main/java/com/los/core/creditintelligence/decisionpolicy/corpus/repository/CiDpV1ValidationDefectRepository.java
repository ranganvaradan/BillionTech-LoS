package com.los.core.creditintelligence.decisionpolicy.corpus.repository;

import com.los.core.creditintelligence.decisionpolicy.corpus.domain.CiDpV1ValidationDefect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiDpV1ValidationDefectRepository extends JpaRepository<CiDpV1ValidationDefect, UUID> {
    List<CiDpV1ValidationDefect> findByRunIdOrderByCreatedAtDesc(UUID runId);
}
