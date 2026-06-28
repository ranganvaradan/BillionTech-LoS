package com.los.core.repository;

import com.los.core.model.entity.ApplicationDeletionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationDeletionLogRepository extends JpaRepository<ApplicationDeletionLog, UUID> {

    Page<ApplicationDeletionLog> findAllByOrderByDeletedAtDesc(Pageable pageable);
}
