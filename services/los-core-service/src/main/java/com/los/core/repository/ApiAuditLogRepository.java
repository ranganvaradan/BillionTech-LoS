package com.los.core.repository;

import com.los.core.model.entity.ApiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiAuditLogRepository extends JpaRepository<ApiAuditLog, UUID> {

    List<ApiAuditLog> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<ApiAuditLog> findByProviderNameAndApiNameOrderByCreatedAtDesc(String providerName, String apiName);

    Optional<ApiAuditLog> findTopByApiNameAndRequestPayloadContainingAndCreatedAtAfterOrderByCreatedAtDesc(
            String apiName, String requestFragment, Instant after);

    @Modifying
    @Query("delete from ApiAuditLog a where a.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
