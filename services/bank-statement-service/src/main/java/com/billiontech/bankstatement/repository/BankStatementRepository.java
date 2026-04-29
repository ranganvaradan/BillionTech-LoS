package com.billiontech.bankstatement.repository;

import com.billiontech.bankstatement.model.entity.BankStatement;
import com.billiontech.bankstatement.model.enums.ParsingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {
    List<BankStatement> findByApplicationId(String applicationId);
    List<BankStatement> findByBatchId(String batchId);
    Page<BankStatement> findByParsingStatus(ParsingStatus status, Pageable pageable);
    Page<BankStatement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
