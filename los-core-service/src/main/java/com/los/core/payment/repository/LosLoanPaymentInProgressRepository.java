package com.los.core.payment.repository;

import com.los.core.payment.model.LosLoanPaymentInProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LosLoanPaymentInProgressRepository extends JpaRepository<LosLoanPaymentInProgress, UUID> {

    List<LosLoanPaymentInProgress> findByPipStatusOrderByCreatedAtDesc(String pipStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LosLoanPaymentInProgress p where p.applicationId = :applicationId")
    int deleteByApplicationId(@Param("applicationId") UUID applicationId);
}
