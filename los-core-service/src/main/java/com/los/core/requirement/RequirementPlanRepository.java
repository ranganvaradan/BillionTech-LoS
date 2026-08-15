package com.los.core.requirement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequirementPlanRepository extends JpaRepository<RequirementPlanEntity, UUID> {

    List<RequirementPlanEntity> findByApplicationIdOrderByPlanVersionDesc(UUID applicationId);

    @Query("SELECT DISTINCT p FROM RequirementPlanEntity p LEFT JOIN FETCH p.items WHERE p.id = :id")
    Optional<RequirementPlanEntity> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM RequirementPlanEntity p LEFT JOIN FETCH p.items WHERE p.applicationId = :applicationId ORDER BY p.planVersion DESC")
    List<RequirementPlanEntity> findByApplicationIdWithItems(@Param("applicationId") UUID applicationId);
}
