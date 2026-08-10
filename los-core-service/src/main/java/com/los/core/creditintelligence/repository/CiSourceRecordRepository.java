package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiSourceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiSourceRecordRepository extends JpaRepository<CiSourceRecord, UUID> {

    List<CiSourceRecord> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<CiSourceRecord> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);

    Optional<CiSourceRecord> findByTenantIdAndApplicationIdAndIdempotencyKey(
            UUID tenantId, UUID applicationId, String idempotencyKey);
}
