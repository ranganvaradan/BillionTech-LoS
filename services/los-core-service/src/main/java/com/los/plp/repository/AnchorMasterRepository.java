package com.los.plp.repository;

import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnchorMasterRepository extends JpaRepository<AnchorMaster, UUID> {

    List<AnchorMaster> findByPlpAnchorSyncStatus(PlpSyncStatus status);

    Optional<AnchorMaster> findBySourceAnchorApplicationId(UUID sourceAnchorApplicationId);
}
