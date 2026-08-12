package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyStudioSessionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CiPolicyStudioSessionSnapshotRepository
        extends JpaRepository<CiPolicyStudioSessionSnapshot, UUID> {
}
