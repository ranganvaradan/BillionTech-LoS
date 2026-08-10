package com.los.plp.service;

import com.los.plp.config.PlpProperties;
import com.los.plp.event.PlpMasterSyncRequestedEvent;
import com.los.plp.event.PlpMasterSyncType;
import com.los.plp.model.dto.ProgramMasterRequest;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.repository.ProgramMasterRepository;
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
class ProgramMasterServiceAfterCommitTest {

    @Mock
    private ProgramMasterRepository programMasterRepository;
    @Mock
    private PlpProperties plpProperties;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProgramMasterService service;

    @BeforeEach
    void setUp() {
        service = new ProgramMasterService(programMasterRepository, plpProperties, eventPublisher);
    }

    @Test
    void create_publishesProgramSyncEvent() {
        UUID lenderId = UUID.fromString("2d6e05e3-423f-4946-8027-8cbb4f01aefa");
        when(plpProperties.isEnabled()).thenReturn(true);
        when(plpProperties.getLenderId()).thenReturn(lenderId.toString());
        UUID id = UUID.randomUUID();
        when(programMasterRepository.save(any())).thenAnswer(inv -> {
            ProgramMaster p = inv.getArgument(0);
            p.setId(id);
            return p;
        });

        ProgramMasterRequest request = new ProgramMasterRequest();
        request.setProgramCode("PRG-T1");
        request.setProgramName("Invoice Discounting");
        request.setProductType("INVOICE_DISCOUNTING");
        request.setProgramLimit(new BigDecimal("1000000"));

        service.create(request);

        ArgumentCaptor<PlpMasterSyncRequestedEvent> captor =
                ArgumentCaptor.forClass(PlpMasterSyncRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(PlpMasterSyncType.PROGRAM);
        assertThat(captor.getValue().entityId()).isEqualTo(id);
    }
}
