package com.los.core.payment.repository;

import com.los.core.payment.model.LosLoanPaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LosLoanPaymentTransactionRepository extends JpaRepository<LosLoanPaymentTransaction, UUID> {

    Optional<LosLoanPaymentTransaction> findByPgTransactionRef(String pgTransactionRef);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LosLoanPaymentTransaction t where t.applicationId = :applicationId")
    int deleteByApplicationId(@Param("applicationId") UUID applicationId);
}
