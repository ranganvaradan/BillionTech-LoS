package com.los.core.creditintelligence.banking.repository;

import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CiBankTransactionRepository extends JpaRepository<CiBankTransaction, UUID> {
    List<CiBankTransaction> findByBankAccountIdOrderByTransactionDateAsc(UUID bankAccountId);

    List<CiBankTransaction> findByBankAccountIdAndDuplicateStatusNotOrderByTransactionDateAsc(
            UUID bankAccountId, String duplicateStatus);

    Page<CiBankTransaction> findByBankAccountId(UUID bankAccountId, Pageable pageable);

    Page<CiBankTransaction> findByBankAccountIdAndTransactionDateBetween(
            UUID bankAccountId, LocalDate from, LocalDate to, Pageable pageable);

    List<CiBankTransaction> findByBankAccountIdIn(List<UUID> bankAccountIds);

    long countByBankAccountId(UUID bankAccountId);
}
