package com.los.core.repository;

import com.los.core.model.entity.EntityRecordAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EntityRecordAuditRepository
        extends JpaRepository<EntityRecordAudit, UUID>, JpaSpecificationExecutor<EntityRecordAudit> {

    Page<EntityRecordAudit> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId, Pageable pageable);
}
