package com.los.plp.service;

import com.los.plp.config.PlpProperties;
import com.los.plp.event.PlpMasterSyncRequestedEvent;
import com.los.plp.event.PlpMasterSyncType;
import com.los.plp.model.dto.AnchorMasterRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnchorMasterServiceAfterCommitTest {

    @Mock
    private AnchorMasterRepository anchorMasterRepository;
    @Mock
    private PlpProperties plpProperties;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AnchorMasterService service;

    @BeforeEach
    void setUp() {
        service = new AnchorMasterService(anchorMasterRepository, plpProperties, eventPublisher);
    }

    @Test
    void create_whenPlpEnabled_publishesAfterCommitEventAndDoesNotSyncInline() {
        when(plpProperties.isEnabled()).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(anchorMasterRepository.save(any())).thenAnswer(inv -> {
            AnchorMaster a = inv.getArgument(0);
            a.setId(id);
            return a;
        });

        AnchorMasterRequest request = new AnchorMasterRequest();
        request.setCode("STG-A1");
        request.setName("Staging Anchor");

        AnchorMaster created = service.create(request);

        assertThat(created.getId()).isEqualTo(id);
        assertThat(created.getPlpAnchorSyncStatus()).isEqualTo(PlpSyncStatus.NOT_SYNCED);

        ArgumentCaptor<PlpMasterSyncRequestedEvent> captor =
                ArgumentCaptor.forClass(PlpMasterSyncRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(PlpMasterSyncType.ANCHOR);
        assertThat(captor.getValue().entityId()).isEqualTo(id);
    }

    @Test
    void create_whenPlpDisabled_doesNotPublishEvent() {
        when(plpProperties.isEnabled()).thenReturn(false);
        when(anchorMasterRepository.save(any())).thenAnswer(inv -> {
            AnchorMaster a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AnchorMasterRequest request = new AnchorMasterRequest();
        request.setCode("STG-A2");
        request.setName("No Sync");

        service.create(request);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
