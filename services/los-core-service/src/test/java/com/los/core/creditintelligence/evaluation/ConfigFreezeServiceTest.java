package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.evaluation.domain.CiConfigFreeze;
import com.los.core.creditintelligence.evaluation.repository.CiConfigFreezeRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigFreezeServiceTest {

    @Mock
    private CiConfigFreezeRepository configFreezeRepository;

    private CreditIntelligenceProperties properties;
    private ConfigFreezeService service;
    private ContentHasher hasher;

    @BeforeEach
    void setUp() {
        properties = new CreditIntelligenceProperties();
        properties.getCanonicalization().getBureau().setLiveUnsecuredThreshold(6);
        properties.getCanonicalization().getBureau().setFreshnessDays(365);
        properties.getCanonicalization().getGst().setGstr1Gstr3bVarianceWarningPct(5.0);
        properties.getCanonicalization().getGst().setTurnoverEligibilityThreshold(new BigDecimal("50000000"));
        hasher = new ContentHasher();
        service = new ConfigFreezeService(properties, configFreezeRepository, hasher);
    }

    @Test
    void freezeCurrentProducesConfigFreezeV1WithBureauAndGstThresholds() {
        UUID tenantId = UUID.randomUUID();
        when(configFreezeRepository.findByTenantIdAndContentHash(eq(tenantId), any()))
                .thenReturn(Optional.empty());
        when(configFreezeRepository.save(any())).thenAnswer(inv -> {
            CiConfigFreeze f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        CiConfigFreeze freeze = service.freezeCurrent(tenantId);

        ArgumentCaptor<CiConfigFreeze> captor = ArgumentCaptor.forClass(CiConfigFreeze.class);
        verify(configFreezeRepository).save(captor.capture());
        CiConfigFreeze saved = captor.getValue();

        assertThat(saved.getSchemaVersion()).isEqualTo(ConfigFreezeService.SCHEMA_VERSION);
        assertThat(saved.getConfigVersion()).isEqualTo(ConfigFreezeService.CONFIG_VERSION);
        assertThat(saved.getContent()).containsEntry("schemaVersion", "CONFIG_FREEZE_V1");

        @SuppressWarnings("unchecked")
        Map<String, Object> canon = (Map<String, Object>) saved.getContent().get("canonicalization");
        @SuppressWarnings("unchecked")
        Map<String, Object> bureau = (Map<String, Object>) canon.get("bureau");
        @SuppressWarnings("unchecked")
        Map<String, Object> gst = (Map<String, Object>) canon.get("gst");

        assertThat(bureau).containsEntry("liveUnsecuredThreshold", 6);
        assertThat(bureau).containsEntry("freshnessDays", 365);
        assertThat(gst).containsEntry("gstr1Gstr3bVarianceWarningPct", 5.0);
        assertThat(gst).containsEntry("turnoverEligibilityThreshold", "50000000");
        assertThat(freeze.getContentHash()).isEqualTo(saved.getContentHash());
    }

    @Test
    void sameContentIsIdempotentViaFindByTenantIdAndContentHash() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> content = service.snapshotContent();
        String hash = hasher.hashMap(content);

        CiConfigFreeze existing = CiConfigFreeze.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .configVersion(ConfigFreezeService.CONFIG_VERSION)
                .content(content)
                .contentHash(hash)
                .schemaVersion(ConfigFreezeService.SCHEMA_VERSION)
                .build();

        when(configFreezeRepository.findByTenantIdAndContentHash(tenantId, hash))
                .thenReturn(Optional.of(existing));

        CiConfigFreeze first = service.freezeCurrent(tenantId);
        CiConfigFreeze second = service.freezeCurrent(tenantId);

        assertThat(first.getId()).isEqualTo(existing.getId());
        assertThat(second.getId()).isEqualTo(existing.getId());
        assertThat(first.getContentHash()).isEqualTo(second.getContentHash());
        verify(configFreezeRepository, never()).save(any());
    }
}
