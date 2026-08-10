package com.los.core.repository;

import com.los.core.model.entity.CreditAppraisalMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditAppraisalMemoRepository extends JpaRepository<CreditAppraisalMemo, UUID> {

    Optional<CreditAppraisalMemo> findByApplicationId(UUID applicationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CreditAppraisalMemo c where c.applicationId = :applicationId")
    int deleteByApplicationId(@Param("applicationId") UUID applicationId);
}
