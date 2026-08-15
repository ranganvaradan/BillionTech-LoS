package com.los.core.requirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequirementStateTransitionRepository extends JpaRepository<RequirementStateTransitionEntity, UUID> {

    List<RequirementStateTransitionEntity> findByItemIdOrderByCreatedAtAsc(UUID itemId);

    List<RequirementStateTransitionEntity> findByItemIdInOrderByCreatedAtAsc(List<UUID> itemIds);
}
