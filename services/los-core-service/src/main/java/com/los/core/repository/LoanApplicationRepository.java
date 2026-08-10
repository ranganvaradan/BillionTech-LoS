package com.los.core.repository;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByApplicationNumber(String applicationNumber);

    Page<LoanApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    Page<LoanApplication> findByBorrowerType(BorrowerType borrowerType, Pageable pageable);

    Page<LoanApplication> findByStatusAndBorrowerType(ApplicationStatus status, BorrowerType borrowerType, Pageable pageable);

    Page<LoanApplication> findByIntakeSegment(IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByStatusAndIntakeSegment(ApplicationStatus status, IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByBorrowerTypeAndIntakeSegment(BorrowerType borrowerType, IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByStatusAndBorrowerTypeAndIntakeSegment(
            ApplicationStatus status, BorrowerType borrowerType, IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByCustomerId(UUID customerId, Pageable pageable);

    Optional<LoanApplication> findFirstByCustomerIdOrderByUpdatedAtDesc(UUID customerId);

    /**
     * Most recently updated application for this borrower that has been synced to PLP and carries a PLP
     * borrower id. Used to map a LOS borrower to their PLP borrower identity for invoice discounting.
     */
    Optional<LoanApplication> findFirstByCustomerIdAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(UUID customerId);

    /**
     * Invoice-discounting borrower with a synced PLP identity (programs / invoice discounting menus).
     */
    Optional<LoanApplication> findFirstByCustomerIdAndLoanProductAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(
            UUID customerId, String loanProduct);

    Optional<LoanApplication> findFirstByCustomerIdAndLoanProductOrderByUpdatedAtDesc(
            UUID customerId, String loanProduct);

    boolean existsByCustomerIdAndLoanProductAndPlpBorrowerIdIsNotNull(UUID customerId, String loanProduct);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.loan_product = :loanProduct
              AND a.personal_info->>'borrowerUserId' = :borrowerUserId
            ORDER BY a.updated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<LoanApplication> findFirstByPersonalInfoBorrowerUserIdAndLoanProductOrderByUpdatedAtDesc(
            @Param("borrowerUserId") String borrowerUserId, @Param("loanProduct") String loanProduct);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.loan_product = :loanProduct
              AND lower(coalesce(a.personal_info->>'email', a.personal_info->>'borrowerEmail',
                                 a.personal_info->>'contactEmail', a.business_info->>'email',
                                 a.business_info->>'contactEmail', a.business_info->>'contactPersonEmail')) = lower(:email)
            ORDER BY a.updated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<LoanApplication> findFirstByBorrowerEmailAndLoanProductOrderByUpdatedAtDesc(
            @Param("email") String email, @Param("loanProduct") String loanProduct);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.loan_product = :loanProduct
              AND regexp_replace(coalesce(
                  a.personal_info->>'mobile',
                  a.personal_info->>'borrowerMobile',
                  a.personal_info->>'phone',
                  a.business_info->>'mobile',
                  a.business_info->>'contactMobile'), '[^0-9]', '', 'g') = :mobileDigits
            ORDER BY a.updated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<LoanApplication> findFirstByBorrowerMobileAndLoanProductOrderByUpdatedAtDesc(
            @Param("mobileDigits") String mobileDigits, @Param("loanProduct") String loanProduct);

    /**
     * Invoice-discounting (or PLP-linked) applications for a borrower contact, regardless of stale
     * {@code customer_id}, when PLP borrower sync has succeeded.
     */
    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.sub_program_id IS NOT NULL
              AND a.plp_borrower_sync_status = 'SYNC_SUCCESS'
              AND (
                a.customer_id = CAST(:borrowerUserId AS uuid)
                OR a.personal_info->>'borrowerUserId' = CAST(:borrowerUserId AS text)
                OR (:email <> '' AND lower(coalesce(
                    a.personal_info->>'email', a.personal_info->>'borrowerEmail',
                    a.personal_info->>'contactEmail', a.business_info->>'email',
                    a.business_info->>'contactEmail', a.business_info->>'contactPersonEmail')) = lower(:email))
                OR (:mobileDigits <> '' AND regexp_replace(coalesce(
                    a.personal_info->>'mobile', a.personal_info->>'borrowerMobile',
                    a.personal_info->>'phone', a.business_info->>'mobile',
                    a.business_info->>'contactMobile'), '[^0-9]', '', 'g') = :mobileDigits)
              )
            ORDER BY a.updated_at DESC
            LIMIT 10
            """, nativeQuery = true)
    java.util.List<LoanApplication> findPlpSyncedApplicationsForBorrowerContact(
            @Param("borrowerUserId") UUID borrowerUserId,
            @Param("email") String email,
            @Param("mobileDigits") String mobileDigits);

    Optional<LoanApplication> findTopByVkycTransactionId(String vkycTransactionId);

    @Query("SELECT a.status, COUNT(a) FROM LoanApplication a GROUP BY a.status")
    java.util.List<Object[]> countByStatusGrouped();

    long countByStatus(ApplicationStatus status);

    long countByStatusAndCreatedAtBefore(ApplicationStatus status, java.time.Instant before);

    java.util.List<LoanApplication> findByEscalatedFalseAndSlaDeadlineBefore(java.time.Instant deadline);

    java.util.List<LoanApplication> findByStatusIn(java.util.List<ApplicationStatus> statuses);

    long countBySubProgramId(UUID subProgramId);

    java.util.List<LoanApplication> findBySubProgramId(UUID subProgramId);

    java.util.List<LoanApplication> findBySubProgramIdIn(java.util.Collection<UUID> subProgramIds);

    // ----- Duplicate-identity detection at submission (email / PAN / GSTN) -----
    // JSONB lookups are case-insensitive and ignore the application being submitted. They return
    // candidate matches so the service layer can decide whether the match is the same customer
    // (allowed) or a different borrower (blocked).

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.id <> :selfId
              AND lower(coalesce(a.personal_info->>'email', a.personal_info->>'borrowerEmail',
                                 a.personal_info->>'contactEmail', a.business_info->>'email')) = lower(:email)
            """, nativeQuery = true)
    java.util.List<LoanApplication> findOthersByEmail(@org.springframework.data.repository.query.Param("selfId") UUID selfId,
                                                      @org.springframework.data.repository.query.Param("email") String email);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.id <> :selfId
              AND upper(coalesce(a.personal_info->>'panNumber', a.business_info->>'entityPan')) = upper(:pan)
            """, nativeQuery = true)
    java.util.List<LoanApplication> findOthersByPan(@org.springframework.data.repository.query.Param("selfId") UUID selfId,
                                                    @org.springframework.data.repository.query.Param("pan") String pan);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.id <> :selfId
              AND upper(coalesce(a.personal_info->>'gstin', a.business_info->>'gstin')) = upper(:gstin)
            """, nativeQuery = true)
    java.util.List<LoanApplication> findOthersByGstin(@org.springframework.data.repository.query.Param("selfId") UUID selfId,
                                                      @org.springframework.data.repository.query.Param("gstin") String gstin);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.id <> :selfId
              AND regexp_replace(coalesce(
                  a.personal_info->>'mobile',
                  a.personal_info->>'borrowerMobile',
                  a.personal_info->>'phone',
                  a.business_info->>'mobile',
                  a.business_info->>'contactMobile'), '[^0-9]', '', 'g') = :mobileDigits
            """, nativeQuery = true)
    java.util.List<LoanApplication> findOthersByMobile(@org.springframework.data.repository.query.Param("selfId") UUID selfId,
                                                       @org.springframework.data.repository.query.Param("mobileDigits") String mobileDigits);

    long countByCustomerId(UUID customerId);
}
