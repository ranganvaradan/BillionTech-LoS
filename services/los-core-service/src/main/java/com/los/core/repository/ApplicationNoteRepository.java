package com.los.core.repository;

import com.los.core.model.entity.ApplicationNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, UUID> {

    List<ApplicationNote> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<ApplicationNote> findByApplicationIdAndInternalFalseOrderByCreatedAtDesc(UUID applicationId);
}
