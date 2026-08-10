package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiStandardRuleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiStandardRuleResultRepository extends JpaRepository<CiStandardRuleResult, UUID> {

    List<CiStandardRuleResult> findByEvaluationId(UUID evaluationId);
}
