package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
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
class PinnedMetricLookupTest {

    @Mock
    private CiMetricResultRepository metricResultRepository;
    @Mock
    private MetricResultSetService metricResultSetService;

    private PinnedMetricLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new PinnedMetricLookup(metricResultRepository, metricResultSetService);
    }

    @Test
    void pinnedSetReturnsOriginalEvenWhenLatestDiffers() {
        UUID appId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        UUID pinnedMetricId = UUID.randomUUID();

        CiMetricResult pinned = CiMetricResult.builder()
                .id(pinnedMetricId)
                .applicationId(appId)
                .tenantId(UUID.randomUUID())
                .metricCode("METRIC_A")
                .metricVersion("V1")
                .outcome("PASS")
                .value(Map.of("v", 100))
                .build();

        Map<String, CiMetricResult> pinnedMap = new LinkedHashMap<>();
        pinnedMap.put("METRIC_A", pinned);
        when(metricResultSetService.lookupBySet(setId)).thenReturn(pinnedMap);

        // Latest row exists conceptually but must not be consulted when pinned
        CiMetricResult latestAppended = CiMetricResult.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .tenantId(pinned.getTenantId())
                .metricCode("METRIC_A")
                .metricVersion("V1")
                .outcome("PASS")
                .value(Map.of("v", 999))
                .build();
        assertThat(latestAppended.getValue()).containsEntry("v", 999);

        CiEvaluationContext ctx = CiEvaluationContext.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .metricResultSetId(setId)
                .build();

        Optional<CiMetricResult> found = lookup.find(appId, "METRIC_A", ctx);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(pinnedMetricId);
        assertThat(found.get().getValue()).containsEntry("v", 100);
        verify(metricResultRepository, never())
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void withoutContextFallsBackToLatest() {
        UUID appId = UUID.randomUUID();
        CiMetricResult latest = CiMetricResult.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .tenantId(UUID.randomUUID())
                .metricCode("METRIC_A")
                .metricVersion("V1")
                .outcome("PASS")
                .value(Map.of("v", 42))
                .build();
        when(metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(appId, "METRIC_A"))
                .thenReturn(Optional.of(latest));

        Optional<CiMetricResult> found = lookup.find(appId, "METRIC_A", null);

        assertThat(found).contains(latest);
        verify(metricResultSetService, never()).lookupBySet(any());
        verify(metricResultRepository)
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(eq(appId), eq("METRIC_A"));
    }
}
