package com.los.core.repository;

import com.los.core.model.entity.ProcessOverrideHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessOverrideHistoryRepository extends JpaRepository<ProcessOverrideHistory, UUID> {
    List<ProcessOverrideHistory> findByApplicationIdOrderByOverriddenAtDesc(UUID applicationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProcessOverrideHistory h where h.applicationId = :applicationId")
    int deleteByApplicationId(@Param("applicationId") UUID applicationId);
}
