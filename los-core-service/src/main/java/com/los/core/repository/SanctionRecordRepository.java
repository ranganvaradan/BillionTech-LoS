package com.los.core.repository;

import com.los.core.model.entity.SanctionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SanctionRecordRepository extends JpaRepository<SanctionRecord, UUID> {

    Optional<SanctionRecord> findTopByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
