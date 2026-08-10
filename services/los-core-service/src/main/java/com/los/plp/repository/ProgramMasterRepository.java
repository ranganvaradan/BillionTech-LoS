package com.los.plp.repository;

import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.enums.ProgramApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgramMasterRepository extends JpaRepository<ProgramMaster, UUID> {

    List<ProgramMaster> findByApprovalStatus(ProgramApprovalStatus status);

    List<ProgramMaster> findByAssignedL1UserId(UUID userId);

    List<ProgramMaster> findByAssignedL2UserId(UUID userId);
}
