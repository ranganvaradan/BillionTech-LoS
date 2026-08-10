package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiFactSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiFactSnapshotRepository extends JpaRepository<CiFactSnapshot, UUID> {

    List<CiFactSnapshot> findByApplicationIdOrderBySnapshotVersionDesc(UUID applicationId);

    Optional<CiFactSnapshot> findByApplicationIdAndSnapshotVersion(UUID applicationId, Integer snapshotVersion);

    Optional<CiFactSnapshot> findTopByApplicationIdOrderBySnapshotVersionDesc(UUID applicationId);

    /**
     * Pessimistic lock on the latest snapshot for an application (version bump / freeze races).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM CiFactSnapshot s
            WHERE s.applicationId = :applicationId
              AND s.snapshotVersion = (
                  SELECT MAX(s2.snapshotVersion) FROM CiFactSnapshot s2
                  WHERE s2.applicationId = :applicationId
              )
            """)
    Optional<CiFactSnapshot> findForUpdateMaxVersion(@Param("applicationId") UUID applicationId);
}
