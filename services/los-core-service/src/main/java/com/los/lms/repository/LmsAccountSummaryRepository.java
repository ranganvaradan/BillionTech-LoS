package com.los.lms.repository;

import com.los.lms.entity.LmsAccountSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LmsAccountSummaryRepository extends JpaRepository<LmsAccountSummary, UUID> {
    Optional<LmsAccountSummary> findByApplicationNumber(String applicationNumber);
    Optional<LmsAccountSummary> findByEncoreAccountId(String encoreAccountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LmsAccountSummary s where s.applicationNumber = :applicationNumber")
    int deleteByApplicationNumber(@Param("applicationNumber") String applicationNumber);
}
