package com.los.core.repository;

import com.los.core.model.entity.ApplicationNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, UUID> {

    List<ApplicationNote> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<ApplicationNote> findByApplicationIdAndInternalFalseOrderByCreatedAtDesc(UUID applicationId);

    @Modifying
    @Query("delete from ApplicationNote n where n.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
