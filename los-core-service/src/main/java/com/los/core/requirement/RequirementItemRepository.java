package com.los.core.requirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequirementItemRepository extends JpaRepository<RequirementItemEntity, UUID> {

    List<RequirementItemEntity> findByPlanIdOrderBySortOrderAscCreatedAtAsc(UUID planId);

    Optional<RequirementItemEntity> findByIdAndPlanId(UUID id, UUID planId);

    List<RequirementItemEntity> findByPlanIdAndDocumentRef(UUID planId, String documentRef);

    List<RequirementItemEntity> findByDocumentRef(String documentRef);
}
