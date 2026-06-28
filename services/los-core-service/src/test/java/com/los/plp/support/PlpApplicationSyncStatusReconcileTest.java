package com.los.plp.support;

import com.los.core.model.entity.LoanApplication;
import com.los.plp.model.enums.PlpSyncStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlpApplicationSyncStatusReconcileTest {

    @Test
    void reconcile_setsSyncSuccessWhenIdsExistButStatusNotSynced() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .plpBorrowerId(UUID.randomUUID())
                .plpSubProgramBorrowerId(UUID.randomUUID())
                .plpBorrowerProgramMappingId(UUID.randomUUID())
                .plpBorrowerSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .plpLinkSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .plpMappingSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .plpProgramSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();

        boolean changed = PlpApplicationSyncStatusReconcile.reconcile(app);

        assertThat(changed).isTrue();
        assertThat(app.getPlpBorrowerSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(app.getPlpLinkSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(app.getPlpMappingSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(app.getPlpProgramSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(app.getPlpBorrowerSyncedAt()).isNotNull();
        assertThat(app.getPlpLinkSyncedAt()).isNotNull();
        assertThat(app.getPlpMappingSyncedAt()).isNotNull();
        assertThat(app.getPlpProgramSyncedAt()).isNotNull();
    }

    @Test
    void reconcile_noOpWhenAlreadySynced() {
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .plpBorrowerId(UUID.randomUUID())
                .plpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .build();

        boolean changed = PlpApplicationSyncStatusReconcile.reconcile(app);

        assertThat(changed).isFalse();
    }
}
