package com.los.plp.repository;

import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SubProgramMasterRepository extends JpaRepository<SubProgramMaster, UUID> {

    List<SubProgramMaster> findByProgramId(UUID programId);

    List<SubProgramMaster> findByAnchorId(UUID anchorId);

    List<SubProgramMaster> findByAnchorIdAndPlpSubProgramSyncStatus(UUID anchorId, PlpSyncStatus status);

    /**
     * Idempotency lookup for {@code PlpProgramSetupService}: find sub-programs whose parent {@code ProgramMaster}
     * has the given {@code programName} (trim + case-insensitive) under the given anchor. Used so that retrying
     * a program creation with the same anchor + program name returns the existing record instead of attempting
     * a new INSERT (which would fail on {@code program_masters.program_code} unique constraint).
     */
    @Query("SELECT s FROM SubProgramMaster s "
            + "WHERE s.anchorId = :anchorId "
            + "AND s.programId IN ("
            + "  SELECT p.id FROM ProgramMaster p "
            + "  WHERE LOWER(TRIM(p.programName)) = LOWER(TRIM(:programName))"
            + ")")
    List<SubProgramMaster> findByAnchorIdAndProgramName(
            @Param("anchorId") UUID anchorId,
            @Param("programName") String programName);
}
