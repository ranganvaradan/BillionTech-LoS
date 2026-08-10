package com.los.core.creditintelligence;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.repository.CiSourceArtifactRepository;
import com.los.core.creditintelligence.repository.CiSourceRecordRepository;
import com.los.core.creditintelligence.service.SourceRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SourceRegistryServiceTest {

    @Mock
    private CiSourceRecordRepository sourceRecordRepository;
    @Mock
    private CiSourceArtifactRepository sourceArtifactRepository;

    private SourceRegistryService service;
    private UUID tenantId;
    private UUID applicationId;

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        service = new SourceRegistryService(sourceRecordRepository, sourceArtifactRepository, props);
        tenantId = props.getDefaultTenantId();
        applicationId = UUID.randomUUID();
    }

    @Test
    void createOrGet_persistsSanitizedMetadata_stripsSensitiveKeys() {
        when(sourceRecordRepository.save(any())).thenAnswer(inv -> {
            CiSourceRecord r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        CiSourceRecord saved = service.createOrGet(
                tenantId,
                applicationId,
                SourceType.CONSUMER_BUREAU.name(),
                "EQUIFAX",
                "UNDERWRITING",
                "bureau-1",
                Map.of(
                        "table", "bureau_reports",
                        "panNumber", "ABCDE1234F",
                        "rawPayload", "{secret}",
                        "providerReference", "EQ-9"),
                "tester");

        ArgumentCaptor<CiSourceRecord> cap = ArgumentCaptor.forClass(CiSourceRecord.class);
        verify(sourceRecordRepository).save(cap.capture());
        Map<String, Object> meta = cap.getValue().getMetadata();
        assertEquals("bureau_reports", meta.get("table"));
        assertEquals("EQ-9", meta.get("providerReference"));
        assertFalse(meta.containsKey("panNumber"));
        assertFalse(meta.containsKey("rawPayload"));
        assertNotNull(saved.getId());
    }

    @Test
    void createOrGet_idempotent_returnsExisting() {
        UUID existingId = UUID.randomUUID();
        CiSourceRecord existing = CiSourceRecord.builder()
                .id(existingId)
                .tenantId(tenantId)
                .applicationId(applicationId)
                .sourceType(SourceType.APPLICATION.name())
                .providerCode("LOS")
                .purpose("UNDERWRITING")
                .idempotencyKey("app-ctx-1")
                .metadata(Map.of())
                .build();
        when(sourceRecordRepository.findByTenantIdAndApplicationIdAndIdempotencyKey(
                tenantId, applicationId, "app-ctx-1"))
                .thenReturn(Optional.of(existing));

        CiSourceRecord got = service.createOrGet(
                tenantId, applicationId, SourceType.APPLICATION.name(), "LOS",
                "UNDERWRITING", "app-ctx-1", Map.of(), "tester");

        assertEquals(existingId, got.getId());
        verify(sourceRecordRepository, never()).save(any());
    }

    @Test
    void findByIdForTenant_isolatesTenants() {
        UUID otherTenant = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        CiSourceRecord record = CiSourceRecord.builder()
                .id(id)
                .tenantId(tenantId)
                .applicationId(applicationId)
                .sourceType(SourceType.KYC.name())
                .providerCode("KARZA")
                .purpose("UNDERWRITING")
                .metadata(Map.of())
                .build();
        when(sourceRecordRepository.findById(id)).thenReturn(Optional.of(record));

        assertTrue(service.findByIdForTenant(tenantId, id).isPresent());
        assertTrue(service.findByIdForTenant(otherTenant, id).isEmpty());
    }

    @Test
    void sanitizeMetadata_doesNotLogSensitiveKeysInStaticHelper() {
        Map<String, Object> cleaned = SourceRegistryService.sanitizeMetadata(
                Map.of("aadhaarHash", "x", "originService", "CreditControlService"));
        assertEquals(1, cleaned.size());
        assertEquals("CreditControlService", cleaned.get("originService"));
    }
}
