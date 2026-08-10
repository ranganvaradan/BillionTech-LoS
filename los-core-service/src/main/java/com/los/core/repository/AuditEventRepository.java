package com.los.core.repository;

import com.los.core.model.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId, Pageable pageable);

    List<AuditEvent> findAllByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<AuditEvent> findTopByApplicationIdAndEventTypeOrderByCreatedAtDesc(
            UUID applicationId, String eventType);

    @Modifying
    @Query("delete from AuditEvent a where a.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
