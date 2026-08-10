package com.los.plp.event;

import com.los.plp.service.PlpAnchorSyncService;
import com.los.plp.service.PlpProgramSyncService;
import com.los.plp.service.PlpSubProgramSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs PLP master sync after the LOS create/update transaction commits so
 * {@code REQUIRES_NEW} sync transactions can see the inserted rows.
 * Synchronous (not {@code @Async}) so API callers that reload after create see sync status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlpMasterSyncAfterCommitListener {

    private final PlpAnchorSyncService plpAnchorSyncService;
    private final PlpProgramSyncService plpProgramSyncService;
    private final PlpSubProgramSyncService plpSubProgramSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlpMasterSyncRequested(PlpMasterSyncRequestedEvent event) {
        try {
            switch (event.type()) {
                case ANCHOR -> plpAnchorSyncService.sync(event.entityId());
                case PROGRAM -> plpProgramSyncService.sync(event.entityId());
                case SUB_PROGRAM -> plpSubProgramSyncService.sync(event.entityId());
            }
        } catch (Exception ex) {
            // Sync services already persist SYNC_FAILED; keep create/update best-effort.
            log.error("After-commit PLP {} sync failed for {}: {}",
                    event.type(), event.entityId(), ex.getMessage(), ex);
        }
    }
}
