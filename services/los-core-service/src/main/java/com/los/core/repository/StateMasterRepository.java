package com.los.core.repository;

import com.los.core.model.entity.StateMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StateMasterRepository extends JpaRepository<StateMaster, UUID> {

    List<StateMaster> findAllByActiveTrueOrderByStateNameAsc();

    Optional<StateMaster> findByIdAndActiveTrue(UUID id);

    Optional<StateMaster> findByStateNameIgnoreCaseAndActiveTrue(String stateName);
}
