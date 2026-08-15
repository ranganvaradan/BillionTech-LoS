package com.los.core.requirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequirementAcquisitionAttemptRepository
        extends JpaRepository<RequirementAcquisitionAttemptEntity, UUID> {

    Optional<RequirementAcquisitionAttemptEntity> findByExecutionKey(String executionKey);

    List<RequirementAcquisitionAttemptEntity> findByPlanIdOrderByCreatedAtAsc(UUID planId);

    List<RequirementAcquisitionAttemptEntity> findByItemIdOrderByCreatedAtAsc(UUID itemId);

    List<RequirementAcquisitionAttemptEntity> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
