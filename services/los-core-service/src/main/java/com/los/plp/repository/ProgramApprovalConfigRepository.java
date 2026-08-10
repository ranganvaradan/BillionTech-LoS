package com.los.plp.repository;

import com.los.plp.model.entity.ProgramApprovalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProgramApprovalConfigRepository extends JpaRepository<ProgramApprovalConfig, UUID> {

    Optional<ProgramApprovalConfig> findFirstByActiveTrueOrderByCreatedAtDesc();
}
