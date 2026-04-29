package com.billiontech.bankstatement.repository;

import com.billiontech.bankstatement.model.entity.StatementAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatementAnalysisRepository extends JpaRepository<StatementAnalysis, Long> {
    Optional<StatementAnalysis> findByStatementId(Long statementId);
}
