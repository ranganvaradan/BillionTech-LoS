package com.los.lms.repository;

import com.los.lms.entity.LmsRepaymentCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LmsRepaymentCallbackRepository extends JpaRepository<LmsRepaymentCallback, UUID> {
    List<LmsRepaymentCallback> findByApplicationNumberOrderByCreatedAtDesc(String applicationNumber);
    List<LmsRepaymentCallback> findByEncoreAccountIdOrderByCreatedAtDesc(String encoreAccountId);
    Optional<LmsRepaymentCallback> findByIdempotencyKey(String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LmsRepaymentCallback c where c.applicationNumber = :applicationNumber")
    int deleteByApplicationNumber(@Param("applicationNumber") String applicationNumber);
}
