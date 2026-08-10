package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiForm26AsSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiForm26AsSummaryRepository extends JpaRepository<CiForm26AsSummary, UUID> {

    List<CiForm26AsSummary> findByApplicationIdOrderByFinancialYearDesc(UUID applicationId);

    Optional<CiForm26AsSummary> findByTenantIdAndApplicationIdAndIdempotencyKey(
            UUID tenantId, UUID applicationId, String idempotencyKey);
}
