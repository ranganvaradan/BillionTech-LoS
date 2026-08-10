package com.los.core.repository;

import com.los.core.model.entity.CollateralValuation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CollateralValuationRepository extends JpaRepository<CollateralValuation, UUID> {
    List<CollateralValuation> findByApplicationId(UUID applicationId);

    @Modifying
    @Query("delete from CollateralValuation c where c.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
