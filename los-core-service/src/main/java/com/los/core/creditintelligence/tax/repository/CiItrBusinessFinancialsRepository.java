package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiItrBusinessFinancials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CiItrBusinessFinancialsRepository extends JpaRepository<CiItrBusinessFinancials, UUID> {

    Optional<CiItrBusinessFinancials> findByItrReturnId(UUID itrReturnId);
}
