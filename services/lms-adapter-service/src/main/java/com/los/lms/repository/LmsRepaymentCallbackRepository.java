package com.los.lms.repository;

import com.los.lms.entity.LmsRepaymentCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LmsRepaymentCallbackRepository extends JpaRepository<LmsRepaymentCallback, UUID> {
    List<LmsRepaymentCallback> findByApplicationNumberOrderByCreatedAtDesc(String applicationNumber);
    List<LmsRepaymentCallback> findByEncoreAccountIdOrderByCreatedAtDesc(String encoreAccountId);
}
