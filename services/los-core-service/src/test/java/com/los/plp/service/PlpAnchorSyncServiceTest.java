package com.los.plp.service;

import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.response.PlpAnchorSyncData;
import com.los.plp.mapper.PlpAnchorPayloadMapper;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlpAnchorSyncServiceTest {

    @Mock
    private AnchorMasterRepository anchorMasterRepository;
    @Mock
    private PlpIntegrationClient plpIntegrationClient;

    @InjectMocks
    private PlpAnchorSyncService plpAnchorSyncService;

    @Test
    void sync_successStoresPlpAnchorId() {
        UUID anchorId = UUID.randomUUID();
        UUID plpId = UUID.randomUUID();
        AnchorMaster anchor = AnchorMaster.builder()
                .id(anchorId)
                .code("ACME")
                .name("Acme Ltd")
                .build();

        PlpApiResponse<PlpAnchorSyncData> response = new PlpApiResponse<>();
        response.setStatus("SUCCESS");
        PlpAnchorSyncData data = new PlpAnchorSyncData();
        data.setPlpAnchorId(plpId.toString());
        response.setData(data);

        when(anchorMasterRepository.findById(anchorId)).thenReturn(Optional.of(anchor));
        when(plpIntegrationClient.syncAnchor(PlpAnchorPayloadMapper.toRequest(anchor))).thenReturn(response);
        when(anchorMasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnchorMaster result = plpAnchorSyncService.sync(anchorId);

        assertThat(result.getPlpAnchorSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_SUCCESS);
        assertThat(result.getPlpAnchorId()).isEqualTo(plpId);
    }

    @Test
    void sync_notFoundReturnsNull() {
        UUID anchorId = UUID.randomUUID();
        when(anchorMasterRepository.findById(anchorId)).thenReturn(Optional.empty());

        AnchorMaster result = plpAnchorSyncService.sync(anchorId);

        assertThat(result).isNull();
        verify(anchorMasterRepository, never()).save(any());
    }

    @Test
    void sync_failureSetsSyncFailed() {
        UUID anchorId = UUID.randomUUID();
        AnchorMaster anchor = AnchorMaster.builder()
                .id(anchorId)
                .code("ACME")
                .name("Acme Ltd")
                .build();

        when(anchorMasterRepository.findById(anchorId)).thenReturn(Optional.of(anchor));
        when(plpIntegrationClient.syncAnchor(any())).thenThrow(new PlpIntegrationException("PLP down"));
        when(anchorMasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnchorMaster result = plpAnchorSyncService.sync(anchorId);

        assertThat(result.getPlpAnchorSyncStatus()).isEqualTo(PlpSyncStatus.SYNC_FAILED);
        assertThat(result.getPlpAnchorSyncError()).contains("PLP down");
    }
}
