package com.los.core.repository;

import com.los.core.model.entity.StepExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StepExecutionRecordRepository extends JpaRepository<StepExecutionRecord, UUID> {

    List<StepExecutionRecord> findByApplicationIdOrderByStartedAtDesc(UUID applicationId);
    List<StepExecutionRecord> findByApplicationIdAndStepTypeStartingWithOrderByStartedAtAsc(UUID applicationId, String stepTypePrefix);

    @Modifying
    @Query("delete from StepExecutionRecord s where s.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
