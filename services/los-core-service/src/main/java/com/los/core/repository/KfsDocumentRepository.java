package com.los.core.repository;

import com.los.core.model.entity.KfsDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KfsDocumentRepository extends JpaRepository<KfsDocument, UUID> {

    List<KfsDocument> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<KfsDocument> findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(UUID applicationId, String status);

    Optional<KfsDocument> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
