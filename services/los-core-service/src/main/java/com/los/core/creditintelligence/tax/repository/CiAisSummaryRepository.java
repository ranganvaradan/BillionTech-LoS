package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiAisSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiAisSummaryRepository extends JpaRepository<CiAisSummary, UUID> {

    List<CiAisSummary> findByApplicationIdOrderByFinancialYearDesc(UUID applicationId);

    Optional<CiAisSummary> findByTenantIdAndApplicationIdAndIdempotencyKey(
            UUID tenantId, UUID applicationId, String idempotencyKey);
}
