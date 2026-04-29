package com.billiontech.bankstatement.repository;

import com.billiontech.bankstatement.model.entity.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {
    List<MonthlySummary> findByStatementIdOrderByYearAscMonthAsc(Long statementId);
}
