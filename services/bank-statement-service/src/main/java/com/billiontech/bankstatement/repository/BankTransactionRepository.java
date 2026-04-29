package com.billiontech.bankstatement.repository;

import com.billiontech.bankstatement.model.entity.BankTransaction;
import com.billiontech.bankstatement.model.enums.TransactionCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    Page<BankTransaction> findByStatementIdOrderByTransactionDateAsc(Long statementId, Pageable pageable);
    List<BankTransaction> findByStatementIdOrderByTransactionDateAsc(Long statementId);
    List<BankTransaction> findByStatementIdAndCategory(Long statementId, TransactionCategory category);
    long countByStatementId(Long statementId);

    @Query("SELECT t FROM BankTransaction t WHERE t.statement.id = :statementId AND t.isBounce = true")
    List<BankTransaction> findBouncesByStatementId(@Param("statementId") Long statementId);
}
