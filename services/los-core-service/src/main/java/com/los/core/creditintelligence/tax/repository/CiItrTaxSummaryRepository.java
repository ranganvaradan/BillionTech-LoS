package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiItrTaxSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CiItrTaxSummaryRepository extends JpaRepository<CiItrTaxSummary, UUID> {

    Optional<CiItrTaxSummary> findByItrReturnId(UUID itrReturnId);
}
