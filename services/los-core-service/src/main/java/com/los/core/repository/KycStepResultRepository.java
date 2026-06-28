package com.los.core.repository;

import com.los.core.model.entity.KycStepResult;
import com.los.core.model.enums.KycStepType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycStepResultRepository extends JpaRepository<KycStepResult, UUID> {

    List<KycStepResult> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    Optional<KycStepResult> findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(UUID applicationId, KycStepType stepType);

    long countByApplicationIdAndOutcome(UUID applicationId, com.los.core.model.enums.StepOutcome outcome);

    @Modifying
    @Query("delete from KycStepResult k where k.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
