package com.los.iam.repository;

import com.los.iam.entity.LoginAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LoginAuditRepository extends JpaRepository<LoginAudit, UUID> {

    Page<LoginAudit> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
}
