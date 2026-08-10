package com.los.plp.service;

import com.los.plp.config.PlpProperties;
import com.los.plp.event.PlpMasterSyncRequestedEvent;
import com.los.plp.event.PlpMasterSyncType;
import com.los.plp.model.dto.SubProgramMasterRequest;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.SubProgramMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubProgramMasterServiceAfterCommitTest {

    @Mock
    private SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    private PlpProperties plpProperties;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SubProgramMasterService service;

    @BeforeEach
    void setUp() {
        service = new SubProgramMasterService(subProgramMasterRepository, plpProperties, eventPublisher);
    }

    @Test
    void create_publishesSubProgramSyncEvent() {
        when(plpProperties.isEnabled()).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(subProgramMasterRepository.save(any())).thenAnswer(inv -> {
            SubProgramMaster sp = inv.getArgument(0);
            sp.setId(id);
            return sp;
        });

        SubProgramMasterRequest request = new SubProgramMasterRequest();
        request.setSubProgramCode("SP-T1");
        request.setName("PBD");
        request.setProgramId(UUID.randomUUID());
        request.setAnchorId(UUID.randomUUID());
        request.setFlowType("PURCHASE_BILL_DISCOUNTING");
        request.setAnchorRole("SELLER");
        request.setBorrowerRole("BUYER");
        request.setSubProgramLimit(new BigDecimal("500000"));

        service.create(request);

        ArgumentCaptor<PlpMasterSyncRequestedEvent> captor =
                ArgumentCaptor.forClass(PlpMasterSyncRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(PlpMasterSyncType.SUB_PROGRAM);
        assertThat(captor.getValue().entityId()).isEqualTo(id);
    }
}
