package com.los.core.repository;

import com.los.core.model.entity.KfsDocument;
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
public interface KfsDocumentRepository extends JpaRepository<KfsDocument, UUID> {

    List<KfsDocument> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<KfsDocument> findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(UUID applicationId, String status);

    Optional<KfsDocument> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    @Modifying
    @Query("delete from KfsDocument k where k.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
