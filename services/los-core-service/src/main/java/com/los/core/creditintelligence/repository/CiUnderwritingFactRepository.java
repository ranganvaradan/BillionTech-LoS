package com.los.core.creditintelligence.repository;

import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiUnderwritingFactRepository extends JpaRepository<CiUnderwritingFact, UUID> {

    List<CiUnderwritingFact> findBySnapshotId(UUID snapshotId);

    List<CiUnderwritingFact> findBySnapshotIdOrderByCanonicalPathAsc(UUID snapshotId);
}
