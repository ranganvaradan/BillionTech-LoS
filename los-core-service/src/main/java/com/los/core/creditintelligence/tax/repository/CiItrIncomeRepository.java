package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiItrIncome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CiItrIncomeRepository extends JpaRepository<CiItrIncome, UUID> {

    Optional<CiItrIncome> findByItrReturnId(UUID itrReturnId);
}
