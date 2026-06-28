package com.los.plp.repository;

import com.los.plp.model.entity.ProgramMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProgramMasterRepository extends JpaRepository<ProgramMaster, UUID> {
}
