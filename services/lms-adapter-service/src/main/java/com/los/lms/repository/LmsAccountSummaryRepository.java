package com.los.lms.repository;

import com.los.lms.entity.LmsAccountSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LmsAccountSummaryRepository extends JpaRepository<LmsAccountSummary, UUID> {
    Optional<LmsAccountSummary> findByApplicationNumber(String applicationNumber);
    Optional<LmsAccountSummary> findByEncoreAccountId(String encoreAccountId);
}
