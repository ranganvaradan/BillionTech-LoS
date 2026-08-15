package com.los.core.customercategory.selection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationCategoryDisambiguationAnswerRepository
        extends JpaRepository<ApplicationCategoryDisambiguationAnswerEntity, UUID> {

    List<ApplicationCategoryDisambiguationAnswerEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    Optional<ApplicationCategoryDisambiguationAnswerEntity> findByApplicationIdAndQuestionId(
            UUID applicationId, String questionId);
}
