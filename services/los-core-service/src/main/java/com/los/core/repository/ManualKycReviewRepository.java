package com.los.core.repository;

import com.los.core.model.entity.ManualKycReview;
import com.los.core.model.enums.KycStepType;
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
public interface ManualKycReviewRepository extends JpaRepository<ManualKycReview, UUID> {

    List<ManualKycReview> findByApplicationIdOrderByUpdatedAtDesc(UUID applicationId);

    Optional<ManualKycReview> findByApplicationIdAndStepType(UUID applicationId, KycStepType stepType);

    @Modifying
    @Query("delete from ManualKycReview m where m.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
