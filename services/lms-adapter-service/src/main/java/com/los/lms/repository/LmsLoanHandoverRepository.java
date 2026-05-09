package com.los.lms.repository;

import com.los.lms.entity.LmsLoanHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LmsLoanHandoverRepository extends JpaRepository<LmsLoanHandover, UUID> {
    Optional<LmsLoanHandover> findByApplicationNumber(String applicationNumber);
    Optional<LmsLoanHandover> findByEncoreAccountId(String encoreAccountId);
}
