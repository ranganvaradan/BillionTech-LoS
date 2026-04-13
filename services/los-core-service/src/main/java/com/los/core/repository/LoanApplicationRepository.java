package com.los.core.repository;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByApplicationNumber(String applicationNumber);

    Page<LoanApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    Page<LoanApplication> findByBorrowerType(BorrowerType borrowerType, Pageable pageable);

    Page<LoanApplication> findByStatusAndBorrowerType(ApplicationStatus status, BorrowerType borrowerType, Pageable pageable);

    Page<LoanApplication> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM LoanApplication a GROUP BY a.status")
    java.util.List<Object[]> countByStatusGrouped();

    long countByStatus(ApplicationStatus status);
}
