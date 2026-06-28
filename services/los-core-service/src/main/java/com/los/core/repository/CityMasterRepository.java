package com.los.core.repository;

import com.los.core.model.entity.CityMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CityMasterRepository extends JpaRepository<CityMaster, UUID> {

    List<CityMaster> findAllByState_IdAndActiveTrueOrderByCityNameAsc(UUID stateId);
}
