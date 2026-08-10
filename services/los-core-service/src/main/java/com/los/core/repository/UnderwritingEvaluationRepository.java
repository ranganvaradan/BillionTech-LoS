package com.los.core.repository;

import com.los.core.model.entity.UnderwritingEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UnderwritingEvaluationRepository extends JpaRepository<UnderwritingEvaluation, UUID> {

    Optional<UnderwritingEvaluation> findTopByApplicationIdOrderByEvaluatedAtDesc(UUID applicationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UnderwritingEvaluation e where e.applicationId = :applicationId")
    int deleteByApplicationId(@Param("applicationId") UUID applicationId);
}
