package com.los.core.repository;

import com.los.core.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    long countByApplicationId(UUID applicationId);
}
