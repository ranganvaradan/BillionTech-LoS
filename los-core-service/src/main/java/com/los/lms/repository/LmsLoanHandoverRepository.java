package com.los.lms.repository;

import com.los.lms.entity.LmsLoanHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LmsLoanHandoverRepository extends JpaRepository<LmsLoanHandover, UUID> {
    Optional<LmsLoanHandover> findByApplicationNumber(String applicationNumber);
    Optional<LmsLoanHandover> findByEncoreAccountId(String encoreAccountId);
    List<LmsLoanHandover> findAllByEncoreAccountIdIsNotNull();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LmsLoanHandover h where h.applicationNumber = :applicationNumber")
    int deleteByApplicationNumber(@Param("applicationNumber") String applicationNumber);
}
