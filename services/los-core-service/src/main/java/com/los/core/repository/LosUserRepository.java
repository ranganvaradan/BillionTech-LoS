package com.los.core.repository;

import com.los.core.model.entity.LosUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LosUserRepository extends JpaRepository<LosUser, UUID> {

    Optional<LosUser> findByEmailIgnoreCase(String email);

    Optional<LosUser> findByPasswordResetToken(String token);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select case when count(u) > 0 then true else false end from LosUser u where u.mobile is not null and u.mobile = :m")
    boolean existsByNonNullMobile(@Param("m") String mobile);

    @Query("""
            select distinct u from LosUser u, UserRoleMapping m
            where m.userId = u.id
              and m.active = true
              and u.active = true
              and m.losRole = :role
            order by u.name asc
            """)
    List<LosUser> findActiveUsersForRole(@Param("role") String role);

    /** Used to match a prospect phone to a {@link LosUser} without DB-specific phone normalization. */
    List<LosUser> findByMobileIsNotNull();

    /**
     * Removes auto-provisioned borrower accounts while preserving seeded demo borrowers.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM LosUser u
            WHERE u.primaryLosRole = 'BORROWER'
              AND lower(trim(u.email)) NOT IN :preservedEmails
            """)
    int deleteBorrowersNotInPreservedEmails(@Param("preservedEmails") Collection<String> preservedEmails);
}
