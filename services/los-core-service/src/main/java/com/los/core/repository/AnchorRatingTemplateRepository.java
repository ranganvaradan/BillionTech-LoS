package com.los.core.repository;

import com.los.core.model.entity.AnchorRatingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnchorRatingTemplateRepository extends JpaRepository<AnchorRatingTemplate, UUID> {

    Optional<AnchorRatingTemplate> findFirstByActiveTrueOrderByVersionDesc();

    List<AnchorRatingTemplate> findAllByOrderByUpdatedAtDesc();
}
