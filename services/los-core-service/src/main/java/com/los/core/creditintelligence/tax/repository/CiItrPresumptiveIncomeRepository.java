package com.los.core.creditintelligence.tax.repository;

import com.los.core.creditintelligence.tax.domain.CiItrPresumptiveIncome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiItrPresumptiveIncomeRepository extends JpaRepository<CiItrPresumptiveIncome, UUID> {

    List<CiItrPresumptiveIncome> findByItrReturnId(UUID itrReturnId);
}
