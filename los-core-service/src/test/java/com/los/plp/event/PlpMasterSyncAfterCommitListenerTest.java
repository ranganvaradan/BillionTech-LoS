package com.los.plp.event;

import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.service.PlpAnchorSyncService;
import com.los.plp.service.PlpProgramSyncService;
import com.los.plp.service.PlpSubProgramSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlpMasterSyncAfterCommitListenerTest {

    @Mock
    private PlpAnchorSyncService plpAnchorSyncService;
    @Mock
    private PlpProgramSyncService plpProgramSyncService;
    @Mock
    private PlpSubProgramSyncService plpSubProgramSyncService;

    private PlpMasterSyncAfterCommitListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlpMasterSyncAfterCommitListener(
                plpAnchorSyncService, plpProgramSyncService, plpSubProgramSyncService);
    }

    @Test
    void onEvent_syncsAnchorById() {
        UUID id = UUID.randomUUID();
        when(plpAnchorSyncService.sync(id)).thenReturn(AnchorMaster.builder()
                .id(id)
                .plpAnchorSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .build());

        listener.onPlpMasterSyncRequested(new PlpMasterSyncRequestedEvent(PlpMasterSyncType.ANCHOR, id));

        verify(plpAnchorSyncService).sync(id);
        verifyNoInteractions(plpProgramSyncService, plpSubProgramSyncService);
    }

    @Test
    void onEvent_syncsProgramById() {
        UUID id = UUID.randomUUID();
        when(plpProgramSyncService.sync(id)).thenReturn(ProgramMaster.builder().id(id).build());

        listener.onPlpMasterSyncRequested(new PlpMasterSyncRequestedEvent(PlpMasterSyncType.PROGRAM, id));

        verify(plpProgramSyncService).sync(id);
    }

    @Test
    void onEvent_syncsSubProgramById() {
        UUID id = UUID.randomUUID();
        when(plpSubProgramSyncService.sync(id)).thenReturn(SubProgramMaster.builder().id(id).build());

        listener.onPlpMasterSyncRequested(new PlpMasterSyncRequestedEvent(PlpMasterSyncType.SUB_PROGRAM, id));

        verify(plpSubProgramSyncService).sync(id);
    }

    @Test
    void onEvent_plpFailureDoesNotPropagate() {
        UUID id = UUID.randomUUID();
        when(plpAnchorSyncService.sync(id)).thenThrow(new RuntimeException("PLP down"));

        listener.onPlpMasterSyncRequested(new PlpMasterSyncRequestedEvent(PlpMasterSyncType.ANCHOR, id));

        verify(plpAnchorSyncService).sync(id);
    }
}
