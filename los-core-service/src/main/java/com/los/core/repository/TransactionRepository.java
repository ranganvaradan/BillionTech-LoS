package com.los.core.repository;

import com.los.core.model.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.applicationId = :applicationId AND t.transactionType = 'DISBURSEMENT' AND t.status = 'COMPLETED'")
    BigDecimal sumDisbursedAmount(UUID applicationId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.applicationId = :applicationId AND t.transactionType = 'REPAYMENT' AND t.status = 'COMPLETED'")
    BigDecimal sumRepaidAmount(UUID applicationId);

    @Modifying
    @Query("delete from Transaction t where t.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
