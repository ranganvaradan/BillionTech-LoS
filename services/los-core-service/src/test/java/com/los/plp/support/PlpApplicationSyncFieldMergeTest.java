package com.los.plp.support;

import com.los.core.model.entity.LoanApplication;
import com.los.plp.model.enums.PlpSyncStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlpApplicationSyncFieldMergeTest {

    @Test
    void mergeInto_copiesAllPlpSyncColumns() {
        UUID borrowerId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        Instant syncedAt = Instant.parse("2026-06-10T04:00:00Z");

        LoanApplication target = LoanApplication.builder().id(UUID.randomUUID()).build();
        LoanApplication source = LoanApplication.builder()
                .plpBorrowerId(borrowerId)
                .plpSubProgramBorrowerId(linkId)
                .plpBorrowerProgramMappingId(mappingId)
                .plpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .plpProgramSyncError(null)
                .plpProgramSyncedAt(syncedAt)
                .plpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .plpBorrowerSyncedAt(syncedAt)
                .plpLinkSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .plpLinkSyncedAt(syncedAt)
                .plpMappingSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .plpMappingSyncedAt(syncedAt)
                .build();

        PlpApplicationSyncFieldMerge.mergeInto(target, source);

        assertThat(target.getPlpBorrowerId()).isEqualTo(borrowerId);
        assertThat(target.getPlpSubProgramBorrowerId()).isEqualTo(linkId);
        assertThat(target.getPlpBorrowerProgramMappingId()).isEqualTo(mappingId);
        assertThat(target.getPlpBorrowerSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(target.getPlpLinkSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(target.getPlpMappingSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(target.getPlpProgramSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
    }
}
